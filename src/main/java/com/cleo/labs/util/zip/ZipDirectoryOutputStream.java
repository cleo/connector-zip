package com.cleo.labs.util.zip;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.cleo.labs.util.zip.LambdaReaderOutputStream;

public class ZipDirectoryOutputStream extends FilterOutputStream implements LambdaReaderOutputStream.Reader {

    private Path path;
    private String canonical;
    private LambdaReaderOutputStream output;
    private InputStream input;
    private ZipInputStream unzip;
    private ZipEntry entry;
    private File entryFile;
    private FileOutputStream fos;
    private byte[] buffer;
    private boolean closed;

    private static final int ENTRY_NEED = 512;
    private static final int BUFFER_SIZE = 8192;
    private static final int BUFFER_NEED = BUFFER_SIZE;

    public ZipDirectoryOutputStream(Path path) throws IOException {
        super(null);
        this.path = path;
        this.canonical = path.toFile().getCanonicalPath();
        this.output = new LambdaReaderOutputStream(this, ENTRY_NEED);
        this.input = output.getInputStream();
        this.unzip = new ZipInputStream(input);
        this.entry = null;
        this.entryFile = null;
        this.fos = null;
        this.buffer = new byte[BUFFER_SIZE];
        this.closed = false;
        this.out = output;
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
                entryFile = path.resolve(entry.getName()).toFile();
                if (!entryFile.getCanonicalPath().startsWith(canonical)) {
                    throw new IOException("entry name resolves outside target path: "+entry.getName());
                }
                if (entry.isDirectory()) {
                    entryFile.mkdirs();
                    fos = null;
                } else {
                    File parent = new File(entryFile.getParent());
                    if (!parent.exists()) {
                        parent.mkdirs();
                    } else if (!parent.isDirectory()) {
                        throw new IOException("can not create parent directory for "+entry.getName()+": file already exists");
                    }
                    fos = new FileOutputStream(entryFile);
                }
                return BUFFER_NEED;
            }
        } else {
            int n = unzip.read(buffer);
            if (n < 0) {
                unzip.closeEntry();
                if (entry.getTime() >= 0) {
                    entryFile.setLastModified(entry.getTime());
                }
                entry = null;
                entryFile = null;
                if (fos != null) {
                    fos.flush();
                    fos.close();
                    fos = null;
                }
                return ENTRY_NEED;
            } else {
                if (fos != null) {
                    fos.write(buffer, 0, n);
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
        if (fos != null) {
            try {
                fos.flush();
                fos.close();
                fos = null;
            } catch (IOException e) {
                exception = e;
            }
        }
        if (exception != null) {
            throw exception;
        }
    }
}
