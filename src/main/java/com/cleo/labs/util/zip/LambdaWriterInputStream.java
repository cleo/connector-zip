package com.cleo.labs.util.zip;

import java.io.IOException;
import java.io.InputStream;

public class LambdaWriterInputStream extends InputStream {

    public interface Writer {
        public void write(java.io.OutputStream out) throws IOException;
    }

    public static final int DEFAULT_BUFFERSIZE = 8192;

    private Writer writer;
    private byte[] buffer;
    private int bufferSize;
    private int offset;
    private int length;
    private boolean closed;
    private OutputStream output;

    public LambdaWriterInputStream(Writer writer) {
        this(writer, DEFAULT_BUFFERSIZE);
    }

    public LambdaWriterInputStream(Writer writer, int bufferSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("Initial size must be positive: " + bufferSize);
        }
        this.writer = writer;
        this.bufferSize = bufferSize;
        buffer = new byte[bufferSize];
        offset = 0;
        length = 0;
        closed = false;
        output = new OutputStream();
    }

    public void need(int n) throws IOException {
        while (length < n && !closed) {
            writer.write(output);
        }
    }

    @Override
    public int available() throws IOException {
        return closed ? 0 : length;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (off < 0 || len < 0 || len+off > b.length) {
            throw new IndexOutOfBoundsException();
        }
        need(len);
        if (length <= 0) {
            return -1;
        } else if (len > length) {
            len = length;
        }
        System.arraycopy(buffer, offset, b, off, len);
        offset += len;
        length -= len;
        return len;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read() throws IOException {
        need(1);
        if (length > 0) {
            int c = buffer[offset] & 0xff;
            offset++;
            length--;
            return c;
        }
        return -1;
    }

    @Override
    public long skip(long n) throws IOException {
        if (n <= 0) {
            return 0;
        }
        long skipped = 0;
        for (;;) {
            long k = Math.min(n-skipped, (long)length);
            offset += k;
            length -= k;
            skipped += k;
            if (skipped==n || closed) {
                return skipped;
            }
            need(1);
        }
    }

    @Override
    public void close() throws IOException {
        closed = true;
    }

    public java.io.OutputStream getOutputStream() {
        return output;
    }

    public class OutputStream extends java.io.OutputStream {
        /**
         * Returns the smallest multiple of {@code bufferSize} that is
         * at least {@code n}.
         * @param n
         * @return
         */
        private int newLength(int n) {
            return ((n + bufferSize-1) / bufferSize) * bufferSize;
        }

        /**
         * Ensures that there is room in {@code buffer} to append at
         * least {@code n} additional bytes. If this can be accommodated
         * by cleaning up space before {@code offset} in the buffer, the
         * bytes are simply shifted. Otherwise, the buffer is extended
         * and the bytes are copied to offset 0 in the new buffer.
         * @param n the number of bytes room needed
         */
        private void need(int n) {
            if (offset+length+n > buffer.length) {
                byte[] current = buffer;
                if (length+n > buffer.length) {
                    buffer = new byte[newLength(length+n)];
                }
                System.arraycopy(current, offset, buffer, 0, length);
                offset = 0;
            }
        }

        @Override
        public void write(byte[] b, int off, int len) throws IOException {
            if (off < 0 || len < 0 || len+off > b.length) {
                throw new IndexOutOfBoundsException();
            }
            need(len);
            System.arraycopy(b, off, buffer, offset+length, len);
            length += len;
        }

        @Override
        public void write(byte[] b) throws IOException {
            write(b, 0, b.length);
        }

        @Override
        public void write(int b) throws IOException {
            need(1);
            buffer[offset+length] = (byte) b;
            length++;
        }

        @Override
        public void close() throws IOException {
            closed = true;
        }
    }
}
