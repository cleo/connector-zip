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

public class ZipDirectoryInputStream extends FilterInputStream implements LambdaWriterInputStream.Writer {

    private Path path;
    private Iterator<Path> files;
    private OutputStream output;
    private LambdaWriterInputStream input;
    private ZipOutputStream zip;
    private ZipEntry entry;
    private FileInputStream fis;
    private byte[] buffer;

    public ZipDirectoryInputStream(Path path) throws IOException {
        this(path, Deflater.DEFAULT_COMPRESSION);
    }

    public ZipDirectoryInputStream(Path path, int level) throws IOException {
        super(null);
        this.path = path;
        this.files = Files.find(path, Integer.MAX_VALUE, (p, a) -> !p.equals(path) && !a.isSymbolicLink()).iterator();
        this.input = new LambdaWriterInputStream(this);
        this.output = input.getOutputStream();
        this.zip = new ZipOutputStream(output);
        this.entry = null;
        this.fis = null;
        this.buffer = new byte[LambdaWriterInputStream.DEFAULT_BUFFERSIZE];
        zip.setMethod(ZipEntry.DEFLATED);
        zip.setLevel(level);
        this.in = input;
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
