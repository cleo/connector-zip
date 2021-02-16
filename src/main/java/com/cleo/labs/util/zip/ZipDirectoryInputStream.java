package com.cleo.labs.util.zip;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.zip.Deflater;

import com.cleo.labs.util.zip.Finder.DirectoryMode;
import com.google.common.io.ByteStreams;

public class ZipDirectoryInputStream extends FilterInputStream implements LambdaWriterInputStream.Writer {

    public interface Opener {
        public InputStream open(Found file) throws IOException;
    }

    private Finder finder;
    private int limit;
    private Opener opener;
    private int level;

    private OutputStream output;
    private LambdaWriterInputStream input;
    private FoundOutputStream zip;
    private Found entry;
    private InputStream is;
    private byte[] buffer;
    private long currentSize;
    private long totalSize;

    private void setup() throws IOException {
        finder.limit(limit);
        this.input = new LambdaWriterInputStream(this);
        this.in = input;
        this.output = input.getOutputStream();
        this.zip = FoundOutputStream.getFoundOutputStream(output, level);
        this.entry = null;
        this.is = null;
        this.currentSize = 0L;
    }

    private ZipDirectoryInputStream(Finder finder, int limit, Opener opener, int level) throws IOException {
        super(null);
        this.finder = finder;
        this.limit = limit;
        this.opener = opener;
        this.level = level;
        this.buffer = new byte[LambdaWriterInputStream.DEFAULT_BUFFERSIZE];
        this.totalSize = -1L;
        setup();
    }

    public static class Builder {
        private File path = null;
        private Opener opener = f -> new ByteArrayInputStream(new byte[0]);
        private int level = Deflater.DEFAULT_COMPRESSION;
        private Finder finder = null;
        private Predicate<Found> filter = Finder.ALL;
        private DirectoryMode directoryMode = DirectoryMode.include;
        private int limit = 0;
        private InputStream remoteReplica = null;
        private long timeout = 0;
        private TimeUnit unit = null;
        private Consumer<String> debug = s->{};
        public Builder(File path) {
            this.path = path;
        }
        public Builder opener(Opener opener) {
            this.opener = opener;
            return this;
        }
        public Builder level(int level) {
            this.level = level;
            return this;
        }
        public Builder finder(Finder finder) {
            this.finder = finder;
            return this;
        }
        public Builder filter(Predicate<Found> filter) {
            this.filter = filter;
            return this;
        }
        public Builder directoryMode(DirectoryMode directoryMode) {
            this.directoryMode = directoryMode;
            return this;
        }
        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }
        public Builder remoteReplica(InputStream remoteReplica) {
            this.remoteReplica = remoteReplica;
            return this;
        }
        public Builder timeout(long timeout, TimeUnit unit) {
            this.timeout = timeout;
            this.unit = unit;
            return this;
        }
        public Builder debug(Consumer<String> debug) {
            this.debug = debug;
            return this;
        }
        private Finder setupFinder() {
            if (filter == null) {
                filter = Finder.ALL;
            }
            if (directoryMode == null) {
                directoryMode = DirectoryMode.include;
            }
            Finder finder = new Finder(path).filter(filter).directoryMode(directoryMode).debug(debug);
            if (remoteReplica != null) {
                finder.remoteReplica(remoteReplica);
            }
            if (unit != null) {
                finder.timeout(timeout, unit);
            }
            return finder;
        }

        public ZipDirectoryInputStream build() throws IOException {
            if (finder == null) {
                finder = setupFinder();
            }
            return new ZipDirectoryInputStream(finder, limit, opener, level);
        }
    }

    public static Builder builder(File path) {
        return new Builder(path);
    }

    public int count() {
        return finder.count();
    }

    public Finder finder() {
        return finder;
    }

    public long getTotalSize() throws IOException {
        if (totalSize >= 0) {
            return totalSize;
        } else if (currentSize > 0) {
            throw new IOException("totalSize can only be calculated before reading begins");
        } else {
            totalSize = ByteStreams.exhaust(this);
            close();
            return totalSize;
        }
    }

    public boolean isTotalSizeCalculatedYet() {
        return totalSize >= 0;
    }

    public long getCurrentSize() {
        return currentSize;
    }

    @Override
    public void write(OutputStream t) throws IOException, InterruptedException {
        if (entry == null) {
            // time to get the next file and set up a new ZipEntry
            if (finder.hasNext()) {
                Found next = finder.next();
                if (next == null) {
                    // signal that the finder timed out -- go around again
                    throw new InterruptedException();
                } else if (next.directory() && next.fullname().equals("/")) {
                    // skip the root path
                } else if (next.directory()) {
                    entry = next;
                    zip.putNextEntry(entry);
                    zip.closeEntry();
                    entry = null;
                } else {
                    entry = next;
                    zip.putNextEntry(entry);
                    is = (InputStream)opener.open(next);
                }
            } else {
                zip.close();
                zip = null;
                output.close();
                output = null;
            }
        } else {
            // time to shuttle a buffer across
            int n = is.read(buffer);
            if (n < 0) {
                zip.closeEntry();
                is.close();
                entry = null;
                is = null;
            } else {
                zip.write(buffer, 0, n);
            }
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        int n = super.read(b, off, len);
        if (n >= 0) currentSize += n;
        return n;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read() throws IOException {
        int c = super.read();
        if (c >= 0) currentSize++;
        return c;
    }

    @Override
    public void close() throws IOException {
        IOException exception = null;
        if (is != null) {
            try {
                is.close();
                is = null;
            } catch (IOException e) {
                exception = e;
            }
        }
        if (zip != null) {
            try {
                zip.close();
                zip = null;
            } catch (IOException e) {
                exception = e;
            }
        }
        if (finder != null) {
            finder.close();
            finder = null;
        }
        super.close();
        if (exception != null) {
            throw exception;
        }
    }
}
