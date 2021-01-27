package com.cleo.labs.util.zip;

import java.io.File;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.PushbackInputStream;
import java.util.Arrays;
import java.util.function.Function;

import com.google.common.io.ByteStreams;

public abstract class FoundInputStream extends FilterInputStream {

    protected boolean closed = false;
    protected Function<String[],File> resolver = p -> new File(PathUtil.join(p));

    public FoundInputStream(InputStream in) {
        super(in);
    }

    public FoundInputStream resolver(Function<String[],File> resolver) {
        this.resolver = resolver;
        return this;
    }

    abstract public Found getNextEntry() throws IOException;

    abstract public void closeEntry() throws IOException;

    @Override
    public int read() throws IOException {
        return in.read();
    }

    @Override
    public int read(byte[] b) throws IOException {
        return in.read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        return in.read(b, off, len);
    }

    @Override
    public void close() throws IOException {
        super.close();
        closed = true;
    }

    public static final byte[] ZIP_SIGNATURE = new byte[] {0x50, 0x4B, 0x03, 0x04};
    public static final byte[] CLEO_SIGNATURE = new byte[] {0x0C, 0x4C, 0x0E, 0x00};

    public static FoundInputStream getFoundInputStream(InputStream in) throws IOException {
        PushbackInputStream push = new PushbackInputStream(in, ZIP_SIGNATURE.length);
        byte[] sig = new byte[4];
        ByteStreams.readFully(push, sig);
        push.unread(sig);
        if (Arrays.equals(sig, ZIP_SIGNATURE)) {
            return new ZipFoundInputStream(push);
        } else if (Arrays.equals(sig, CLEO_SIGNATURE)) {
            return new ZapFoundInputStream(push);
        }
        throw new IOException("invalid input stream for archive");
    }
}
