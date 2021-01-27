package com.cleo.labs.util.zip;

import java.io.DataOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.OutputStream;

public class EmbeddedOutputStream extends FilterOutputStream {

    public static final int DEFAULT_BUFSIZE = 32 * 1024;

    private int bufsize;
    private byte[] buf;
    private int offset;
    private DataOutputStream dos;

    public EmbeddedOutputStream(OutputStream out) throws IOException {
        this(out, DEFAULT_BUFSIZE);
    }

    public EmbeddedOutputStream(OutputStream out, int bufsize) throws IOException {
        super(null);
        if (bufsize <=0) {
            throw new IllegalArgumentException("bufsize must be positive: "+bufsize);
        }
        dos = new DataOutputStream(out);
        this.out = dos;
        this.bufsize = bufsize;
        this.buf = new byte[bufsize];
        this.offset = 0;
        dos.writeInt(bufsize);
    }

    public int buffer(byte[] b, int off, int len) throws IOException {
        int room = Math.min(bufsize - offset, len);
        System.arraycopy(b, off, buf, offset, room);
        offset += room;
        if (offset >= bufsize) {
            dos.writeInt(offset);
            dos.write(buf, 0, offset);
            offset = 0;
        }
        return room;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (off < 0) {
            throw new IndexOutOfBoundsException();
        }
        while (len > 0) {
            int n = buffer(b, off, len);
            off += n;
            len -= n;
        }
    }

    @Override
    public void write(byte[] b) throws IOException {
        write(b, 0, b.length);
    }

    @Override
    public void write(int b) throws IOException {
        write(new byte[] {(byte)(b&0xFF)}, 0, 1);
    }

    @Override
    public void flush() throws IOException {
        if (offset > 0) {
            dos.writeInt(offset);
            dos.write(buf, 0, offset);
            offset = 0;
        }
        dos.flush();
    }

    @Override
    public void close() throws IOException {
        flush();
        dos.writeInt(-1);
        dos.flush();
        // DO NOT CLOSE filtered stream
    }
}
