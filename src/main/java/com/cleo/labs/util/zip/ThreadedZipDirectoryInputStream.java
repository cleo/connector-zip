package com.cleo.labs.util.zip;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.cleo.labs.util.zip.Finder.DirectoryMode;

public class ThreadedZipDirectoryInputStream extends PipedInputStream {

    public interface Copier {
        public void copy(Found from, OutputStream to) throws IOException;
    }

    private Finder finder;
    private Copier copier;
    private int level;

    private OutputStream output;
    private ZipOutputStream zip;

    private Thread finderThread;
    private Thread zipThread;
    private BlockingQueue<Found> foundQueue;
    private int foundQueueCapacity = 3;
    private int foundQueueTimeout = 1;
    private TimeUnit foundQueueUnit = TimeUnit.SECONDS;

    private IOException exception;
    private boolean closed;

    private Runnable runFinderThread = () -> {
        try {
            for (Found found : finder) {
                if (found != null) {
                    while (!closed && !foundQueue.offer(found, foundQueueTimeout, foundQueueUnit));
                }
                if (closed) {
                    break;
                }
            }
            while (!closed && !foundQueue.offer(Finder.DONE_FINDING, foundQueueTimeout, foundQueueUnit));
        } catch (InterruptedException e) {
            // done
        }
        finder.close();
    };

    private Runnable runZipThread = () -> {
        try {
            Found found;
            do {
                found = foundQueue.poll(foundQueueTimeout, foundQueueUnit);
                if (found == Finder.DONE_FINDING || found == null) {
                    // done, or go back and wait for more
                } else if (found.directory() && found.fullname().equals("/")) {
                    // skip the root path
                } else if (found.directory()) {
                    ZipEntry entry = new ZipEntry(found.fullname());
                    entry.setTime(found.modified());
                    entry.setSize(0L);
                    zip.putNextEntry(entry);
                    zip.closeEntry();
                    entry = null;
                } else {
                    ZipEntry entry = new ZipEntry(found.fullname());
                    entry.setTime(found.modified());
                    entry.setSize(found.length());
                    entry.setCompressedSize(found.length());
                    zip.putNextEntry(entry);
                    copier.copy(found, zip);
                    zip.flush();
                    zip.closeEntry();
                }
            } while (!closed && found != Finder.DONE_FINDING);
        } catch (IOException e) {
            exception = e;
        } catch (InterruptedException e) {
            // done
        }
        if (!closed) {
            try {
                zip.close();
                zip = null;
            } catch (IOException e) {
                if (exception == null) {
                    exception = e;
                }
            }
        }
    };

    private void setup() throws IOException {
        // start the pump from the finder to the foundQueue
        finder.unhold();
        this.closed = false;
        this.foundQueue = new ArrayBlockingQueue<>(foundQueueCapacity);
        this.finderThread = new Thread(runFinderThread, "finderThread");
        finderThread.start();

        // start the zipping thread from the foundQueue to the OutputStream
        this.exception = null;
        this.output = new PipedOutputStream(this);
        this.zip = new ZipOutputStream(output);
        zip.setMethod(ZipEntry.DEFLATED);
        zip.setLevel(level);
        this.zipThread = new Thread(runZipThread, "zipThread");
        zipThread.start();
    }

    private ThreadedZipDirectoryInputStream(Finder finder, Copier copier, int level, int bufferSize) throws IOException {
        super(bufferSize);
        this.finder = finder;
        this.copier = copier;
        this.level = level;
        setup();
    }

    public static final int DEFAULT_BUFFERSIZE = 32 * 1024;

    public static class Builder {
        private File path = null;
        private Copier copier = (f,o)->{};
        private int level = Deflater.DEFAULT_COMPRESSION;
        private int bufferSize = DEFAULT_BUFFERSIZE;
        private Finder finder = null;
        private Predicate<Found> filter = Finder.ALL;
        private DirectoryMode directoryMode = DirectoryMode.include;
        private int[] restart = null;
        private int limit = 0;
        private InputStream remoteReplica = null;
        private long timeout = 0;
        private TimeUnit unit = null;
        private Consumer<String> debug = s->{};
        public Builder(File path) {
            this.path = path;
        }
        public Builder copier(Copier copier) {
            this.copier = copier;
            return this;
        }
        public Builder level(int level) {
            this.level = level;
            return this;
        }
        public Builder bufferSize(int bufferSize) {
            this.bufferSize = bufferSize;
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
        public Builder restart(int[] restart) {
            this.restart = restart;
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
            Finder finder = new Finder(path)
                    .filter(filter)
                    .directoryMode(directoryMode)
                    .limit(limit)
                    .debug(debug);
            if (restart != null && restart.length>0) {
                finder.restart(restart);
            }
            if (remoteReplica != null) {
                finder.remoteReplica(remoteReplica);
            }
            if (unit != null) {
                finder.timeout(timeout, unit);
            }
            return finder;
        }

        public ThreadedZipDirectoryInputStream build() throws IOException {
            if (finder == null) {
                finder = setupFinder();
            }
            return new ThreadedZipDirectoryInputStream(finder, copier, level, bufferSize);
        }
    }

    public static Builder builder(File path) {
        return new Builder(path);
    }

    public ThreadedZipDirectoryInputStream hold() {
        finder.hold();
        return this;
    }

    public int count() {
        return finder.count();
    }

    public int[] checkpoint() {
        return finder.checkpoint();
    }

    public Finder finder() {
        return finder;
    }

    @Override
    public void close() throws IOException {
        closed = true;
        // finderThread will end when closed is true
        // zipThread will end when closed is true
        if (zip != null) {
            try {
                zip.close();
                zip = null;
            } catch (IOException e) {
                if (exception == null) {
                    exception = e;
                }
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
