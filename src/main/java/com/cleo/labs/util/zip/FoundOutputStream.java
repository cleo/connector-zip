package com.cleo.labs.util.zip;

import java.io.File;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.function.Function;
import java.util.zip.ZipEntry;

public abstract class FoundOutputStream extends FilterOutputStream {

    protected boolean closed = false;
    protected Function<String[],File> resolver = p -> new File(PathUtil.join(p));

    public FoundOutputStream(OutputStream out) {
        super(out);
    }

    public FoundOutputStream resolver(Function<String[],File> resolver) {
        this.resolver = resolver;
        return this;
    }

    abstract public void putNextEntry(Found found) throws IOException;

    abstract public void closeEntry() throws IOException;

    @Override
    public void write(byte[] b) throws IOException {
        out.write(b);
    }

    @Override
    public void write(int b) throws IOException {
        out.write(b);
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        out.write(b, off, len);
    }

    public static final int ZAP_LEVEL = -2;

    public static FoundOutputStream getFoundOutputStream(OutputStream out, int level) throws IOException {
        if (level == ZAP_LEVEL) {
            ZapFoundOutputStream zap = new ZapFoundOutputStream(out);
            return zap;
        } else {
            ZipFoundOutputStream zip = new ZipFoundOutputStream(out);
            zip.zip().setMethod(ZipEntry.DEFLATED);
            zip.zip().setLevel(level);
            return zip;
        }
    }
}
