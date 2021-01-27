package com.cleo.labs.util.zip;

import java.io.DataInputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;

public class EmbeddedInputStream extends FilterInputStream {

    private int bufsize;
    private byte[] buf;
    private int offset;
    private int length;
    private DataInputStream dis;

    public EmbeddedInputStream(InputStream in) throws IOException {
        super(null);
        dis = new DataInputStream(in);
        this.in = dis;
        bufsize = dis.readInt();
        this.buf = new byte[bufsize];
        this.length = 0;
    }

    public void buffer() throws IOException {
        offset = 0;
        length = dis.readInt();
        if (length >= 0) {
            dis.readFully(buf, offset, length);
        }
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (off < 0) {
            throw new IndexOutOfBoundsException();
        }
        int n = 0;
        while (n < len && length >= 0) {
            int room = Math.min(length-offset, len-n);
            System.arraycopy(buf, offset, b, off+n, room);
            n += room;
            offset += room;
            if (offset >= length) {
                buffer();
            }
        }
        return n==0 && length<0 ? -1 : n;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read() throws IOException {
        byte[] b = new byte[1];
        if (read(b) < 0) {
            return -1;
        } else {
            return b[0] & 0xFF;
        }
    }

    @Override
    public int available() throws IOException {
        return Math.max(0, length-offset);
    }

    @Override
    public void close() throws IOException {
        // read until the embedded EOF signal
        while (length >= 0) {
            buffer();
        }
        // DO NOT CLOSE filtered stream
    }

}
