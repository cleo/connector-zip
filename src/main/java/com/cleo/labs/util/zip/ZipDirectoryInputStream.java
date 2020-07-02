package com.cleo.labs.util.zip;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Iterator;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.cleo.labs.util.zip.Finder.Found;
import com.google.common.base.Joiner;
import com.google.common.io.ByteStreams;

public class ZipDirectoryInputStream extends FilterInputStream implements LambdaWriterInputStream.Writer {

    public interface Opener {
        public InputStream open(Found file) throws IOException;
    }

    private File path;
    private Opener opener;
    private int level;
    private Iterator<Found> files;
    private OutputStream output;
    private LambdaWriterInputStream input;
    private ZipOutputStream zip;
    private ZipEntry entry;
    private InputStream is;
    private byte[] buffer;
    private long currentSize;
    private long totalSize;

    public ZipDirectoryInputStream(File path, Opener opener) throws IOException {
        this(path, opener, Deflater.DEFAULT_COMPRESSION);
    }

    private void setup() throws IOException {
        this.files = new Finder(path);
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

    public ZipDirectoryInputStream(File path, Opener opener, int level) throws IOException {
        super(null);
        this.path = path;
        this.opener = opener;
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
                String name = Joiner.on('/').join(next.path);
                if (next.directory) {
                    if (!name.isEmpty()) {
                        entry = new ZipEntry(name+"/");
                        entry.setTime(next.file.lastModified());
                        entry.setSize(0L);
                        zip.putNextEntry(entry);
                        zip.closeEntry();
                    }
                    entry = null;
                } else {
                    entry = new ZipEntry(name);
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
