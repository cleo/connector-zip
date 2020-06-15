package com.cleo.labs.util.zip;

import java.io.File;
import java.io.FileInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.common.io.ByteStreams;

public class ZipDirectoryInputStream extends FilterInputStream implements LambdaWriterInputStream.Writer {

    private Path path;
    private int level;
    private Iterator<Path> files;
    private OutputStream output;
    private LambdaWriterInputStream input;
    private ZipOutputStream zip;
    private ZipEntry entry;
    private FileInputStream fis;
    private byte[] buffer;
    private long currentSize;
    private long totalSize;

    public ZipDirectoryInputStream(Path path) throws IOException {
        this(path, Deflater.DEFAULT_COMPRESSION);
    }

    private void setup() throws IOException {
        this.files = Files.find(path, Integer.MAX_VALUE, (p, a) -> !p.equals(path) && !a.isSymbolicLink()).iterator();
        this.input = new LambdaWriterInputStream(this);
        this.in = input;
        this.output = input.getOutputStream();
        this.zip = new ZipOutputStream(output);
        zip.setMethod(ZipEntry.DEFLATED);
        zip.setLevel(level);
        this.entry = null;
        this.fis = null;
        this.currentSize = 0L;
    }

    public ZipDirectoryInputStream(Path path, int level) throws IOException {
        super(null);
        this.path = path;
        this.level = level;
        this.buffer = new byte[LambdaWriterInputStream.DEFAULT_BUFFERSIZE];
        this.totalSize = -1L;
        setup();
    }

    public long getTotalSize() throws IOException {
        if (totalSize >= 0) {
            return totalSize;
        } else if (currentSize > 0) {
            throw new IOException("totalSize can only be calculated before reading begins");
        } else {
            totalSize = ByteStreams.exhaust(this);
            close();
            setup();
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
                Path next = files.next();
                File file = next.toFile();
                String name = path.relativize(next).toString();
                if (file.isDirectory()) {
                    entry = new ZipEntry(name+"/");
                    entry.setTime(file.lastModified());
                    entry.setSize(0L);
                    zip.putNextEntry(entry);
                    zip.closeEntry();
                    entry = null;
                } else {
                    entry = new ZipEntry(name);
                    entry.setTime(file.lastModified());
                    entry.setSize(file.length());
                    entry.setCompressedSize(file.length());
                    zip.putNextEntry(entry);
                    fis = new FileInputStream(file);
                }
            } else {
                zip.close();
                zip = null;
                output.close();
                output = null;
            }
        } else {
            // time to shuttle a buffer across
            int n = fis.read(buffer);
            if (n < 0) {
                zip.closeEntry();
                fis.close();
                entry = null;
                fis = null;
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
        if (fis != null) {
            try {
                fis.close();
                fis = null;
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
