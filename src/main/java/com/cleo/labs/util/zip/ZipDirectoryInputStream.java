package com.cleo.labs.util.zip;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Predicate;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.cleo.labs.util.zip.Finder.DirectoryMode;
import com.cleo.labs.util.zip.Finder.Found;
import com.google.common.io.ByteStreams;

public class ZipDirectoryInputStream extends FilterInputStream implements LambdaWriterInputStream.Writer {

    public interface Opener {
        public InputStream open(Found file) throws IOException;
    }

    private File path;
    private Opener opener;
    private int level;
    private Predicate<Found> filter;
    private int[] restart;
    private int limit;

    private DirectoryMode directoryMode;
    private Finder files;
    private OutputStream output;
    private LambdaWriterInputStream input;
    private ZipOutputStream zip;
    private ZipEntry entry;
    private InputStream is;
    private byte[] buffer;
    private long currentSize;
    private long totalSize;

    private void setup() throws IOException {
        this.files = new Finder(path).filter(filter).directoryMode(directoryMode);
        if (limit > 0) {
            this.files.limit(limit);
        }
        if (restart != null && restart.length>0) {
            this.files.restart(restart);
        }
        this.input = new LambdaWriterInputStream(this);
        this.in = input;
        this.output = input.getOutputStream();
        this.zip = new ZipOutputStream(output);
        zip.setMethod(ZipEntry.DEFLATED);
        zip.setLevel(level);
        this.entry = null;
        this.is = null;
        this.currentSize = 0L;
    }

    public ZipDirectoryInputStream(File path, Opener opener) throws IOException {
        this(path, opener, Deflater.DEFAULT_COMPRESSION, null, null, null, 0);
    }

    public ZipDirectoryInputStream(File path,
            Opener opener,
            int level,
            Predicate<Found> filter,
            DirectoryMode directoryMode,
            int[] restart,
            int limit) throws IOException {
        super(null);
        this.path = path;
        this.opener = opener;
        this.level = level;
        this.buffer = new byte[LambdaWriterInputStream.DEFAULT_BUFFERSIZE];
        this.totalSize = -1L;
        this.filter = filter==null ? Finder.ALL : filter;
        this.directoryMode = directoryMode==null ? DirectoryMode.include : directoryMode;
        this.restart = restart;
        this.limit = limit;
        setup();
    }

    public static class Builder {
        private File path = null;
        private Opener opener = f -> new ByteArrayInputStream(new byte[0]);
        private int level = Deflater.DEFAULT_COMPRESSION;
        private Predicate<Found> filter = Finder.ALL;
        private DirectoryMode directoryMode = DirectoryMode.include;
        private int[] restart = null;
        private int limit = 0;
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
        public Builder filter(Predicate<Found> filter) {
            this.filter = filter;
            return this;
        }
        public Builder directoryMode(DirectoryMode directoryMode) {
            this.directoryMode = directoryMode;
            return this;
        }
        public Builder restart(int[] restart) {
            this.restart = restart;
            return this;
        }
        public Builder limit(int limit) {
            this.limit = limit;
            return this;
        }
        public ZipDirectoryInputStream build() throws IOException {
            return new ZipDirectoryInputStream(path, opener, level, filter, directoryMode, restart, limit);
        }
    }

    public static Builder builder(File path) {
        return new Builder(path);
    }

    public ZipDirectoryInputStream limit(int limit) {
        files.limit(limit);
        return this;
    }

    public int count() {
        return files.count();
    }

    public int[] checkpoint() {
        return files.checkpoint();
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
    public void write(OutputStream t) throws IOException {
        if (entry == null) {
            // time to get the next file and set up a new ZipEntry
            if (files.hasNext()) {
                Found next = files.next();
                if (next.directory) {
                    entry = new ZipEntry(next.fullname);
                    entry.setTime(next.file.lastModified());
                    entry.setSize(0L);
                    zip.putNextEntry(entry);
                    zip.closeEntry();
                    entry = null;
                } else {
                    entry = new ZipEntry(next.fullname);
                    entry.setTime(next.file.lastModified());
                    entry.setSize(next.file.length());
                    entry.setCompressedSize(next.file.length());
                    zip.putNextEntry(entry);
                    is = opener.open(next);
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
        super.close();
        if (exception != null) {
            throw exception;
        }
    }
}
