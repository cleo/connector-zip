package com.cleo.labs.util.zip;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class ZipDirectoryOutputStream extends FilterOutputStream implements LambdaReaderOutputStream.Reader {

    public interface Resolver {
        public File resolve(Path path);
    }

    private LambdaReaderOutputStream output;
    private InputStream input;
    private ZipInputStream unzip;
    private ZipEntry entry;
    private File entryFile;
    private OutputStream os;
    private byte[] buffer;
    private boolean closed;
    private UnZipProcessor processor;
    private Resolver resolver;

    private static final int ENTRY_NEED = 512;
    private static final int BUFFER_SIZE = 8192;
    private static final int BUFFER_NEED = 2*BUFFER_SIZE;

    public ZipDirectoryOutputStream(Resolver resolver) throws IOException {
        super(null);
        this.output = new LambdaReaderOutputStream(this, ENTRY_NEED);
        this.input = output.getInputStream();
        this.unzip = new ZipInputStream(input);
        this.entry = null;
        this.entryFile = null;
        this.os = null;
        this.buffer = new byte[BUFFER_SIZE];
        this.closed = false;
        this.out = output;
        this.processor = defaultProcessor;
        this.resolver = resolver;
    }

    public interface UnZipProcessor {
        public OutputStream process(ZipEntry entry, File entryFile) throws IOException;
    }

    public static UnZipProcessor defaultProcessor = (e, ef) -> {
        if (e.isDirectory()) {
            ef.mkdirs();
            return null;
        } else {
            File parent = ef.getParentFile();
            if (!parent.exists()) {
                parent.mkdirs();
            } else if (!parent.isDirectory()) {
                throw new IOException("can not create parent directory for "+e.getName()+": file already exists");
            }
            return new FileOutputStream(ef);
        }
    };

    public void setProcessor(UnZipProcessor processor) {
        this.processor = processor;
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
                Path entryPath = Paths.get(entry.getName());
                entryFile = resolver.resolve(safeChild(entryPath));
                if (processor != null) {
                    os = processor.process(entry, entryFile);
                }
                return BUFFER_NEED;
            }
        } else {
            int n = unzip.read(buffer);
            if (n < 0) {
                unzip.closeEntry();
                if (entry.getTime() >= 0) {
                    try {
                        entryFile.setLastModified(entry.getTime());
                    } catch (Exception ignore) {
                        // don't worry about it -- some URIs don't allow this
                    }
                }
                entry = null;
                entryFile = null;
                if (os != null) {
                    os.flush();
                    os.close();
                    os = null;
                }
                return ENTRY_NEED;
            } else {
                if (os != null) {
                    os.write(buffer, 0, n);
                }
                return BUFFER_NEED;
            }
        }
    }

    private Path safeChild(Path child) {
        Path root = child.getRoot();
        if (root != null) {
            child = root.relativize(child);
        }
        child = child.normalize();
        while (child.startsWith("..")) {
            child = child.subpath(1, child.getNameCount());
        }
        return child;
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
