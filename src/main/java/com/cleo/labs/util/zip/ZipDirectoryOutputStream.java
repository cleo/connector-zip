package com.cleo.labs.util.zip;

import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Function;
import java.util.function.Predicate;

public class ZipDirectoryOutputStream extends FilterOutputStream implements LambdaReaderOutputStream.Reader {

    private Function<String[],File> resolver;
    private LambdaReaderOutputStream output;
    private InputStream input;
    private FoundInputStream unzip;
    private Found entry;
    private OutputStream os;
    private byte[] buffer;
    private boolean closed;
    private Predicate<Found> filter;
    private UnzipProcessor processor;

    private static final int ENTRY_NEED = 512;
    private static final int BUFFER_SIZE = 8192 * 4;
    private static final int BUFFER_NEED = 2*BUFFER_SIZE;

    public ZipDirectoryOutputStream(Function<String[],File> resolver) throws IOException {
        super(null);
        this.resolver = resolver;
        this.output = new LambdaReaderOutputStream(this, ENTRY_NEED);
        this.input = output.getInputStream();
        this.entry = null;
        this.os = null;
        this.buffer = new byte[BUFFER_SIZE];
        this.closed = false;
        this.out = output;
        this.filter = Finder.ALL;
        this.processor = UnzipProcessor.defaultProcessor;
//      this.unzip = new ZipFoundInputStream(input);
//      unzip.resolver(this.resolver);
    }

    @Override
    public void bootstrap() throws IOException {
        this.unzip = FoundInputStream.getFoundInputStream(input);
        unzip.resolver(resolver);
    }

    public ZipDirectoryOutputStream processor(UnzipProcessor processor) {
        this.processor = processor;
        return this;
    }

    public ZipDirectoryOutputStream filter(Predicate<Found> filter) {
        this.filter = filter;
        return this;
    }

    public int getBufferLength() {
        return output.getBufferLength();
    }

    @Override
    public int read(InputStream in) throws IOException {
        if (closed || unzip == null) {
            input.read(buffer);
            return BUFFER_SIZE;
        } else if (entry == null) {
            entry = unzip.getNextEntry();
            if (entry == null) {
                unzip.close();
                unzip = null;
                return BUFFER_SIZE;
            } else {
                if (processor != null && filter.test(entry)) {
                    os = processor.process(entry);
                }
                return BUFFER_NEED;
            }
        } else {
            int n = unzip.read(buffer);
            if (n < 0) {
                unzip.closeEntry();
                if (os != null) {
                    os.flush();
                    os.close();
                    os = null;
                }
                if (entry.modified() >= 0) {
                    try {
                        entry.file().setLastModified(entry.modified());
                    } catch (Exception ignore) {
                        // don't worry about it -- some URIs don't allow this
                    }
                }
                entry = null;
                return ENTRY_NEED;
            } else {
                if (os != null) {
                    os.write(buffer, 0, n);
                }
                return BUFFER_NEED;
            }
        }
    }

    @Override
    public void close() throws IOException {
        IOException exception = null;
        try {
            super.close();
        } catch (IOException e) {
            exception = e;
        }
        if (unzip != null) {
            try {
                unzip.close();
                entry = null;
            } catch (IOException e) {
                exception = e;
            }
        }
        if (os != null) {
            try {
                os.flush();
                os.close();
                os = null;
            } catch (IOException e) {
                exception = e;
            }
        }
        if (exception != null) {
            throw exception;
        }
    }

}
