package com.cleo.labs.util.zip;

import java.io.IOException;
import java.io.OutputStream;

public class LambdaReaderOutputStream extends OutputStream {

    public interface Reader {
        public int read(java.io.InputStream in) throws IOException;
        default void bootstrap() throws IOException {
        }
    }

    public static final int DEFAULT_BUFFERSIZE = 8192;

    private Reader reader;
    private int need;
    private byte[] buffer;
    private int bufferSize;
    private int offset;
    private int length;
    private boolean closed;
    private InputStream input;
    private boolean bootstrapped;

    public LambdaReaderOutputStream(Reader reader, int need) {
        this(reader, need, DEFAULT_BUFFERSIZE);
    }

    public LambdaReaderOutputStream(Reader reader, int need, int bufferSize) {
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("Initial size must be positive: " + bufferSize);
        }
        this.reader = reader;
        this.need = need;
        this.bufferSize = bufferSize;
        buffer = new byte[bufferSize];
        offset = 0;
        length = 0;
        closed = false;
        input = new InputStream();
        bootstrapped = false;
    }

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

    /**
     * As long as there is enough data in the buffer to meet the
     * requested {@code need}, pass it through to the consuming
     * {@code reader} and update the amount needed for the next
     * chunk.
     * <p/>
     * Once the stream is closed, pass all remaining data to
     * the {@code reader}.
     * @throws IOException
     */
    private void consume() throws IOException {
        while (length >= need || (closed && length > 0)) {
            if (!bootstrapped) {
                reader.bootstrap();
                bootstrapped = true;
            }
            need = reader.read(input);
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
        consume();
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
        consume();
    }

    @Override
    public void close() throws IOException {
        closed = true;
        consume();
    }

    public java.io.InputStream getInputStream() {
        return input;
    }

    public int getBufferLength() {
        return buffer.length;
    }

    public class InputStream extends java.io.InputStream {
        @Override
        public int available() throws IOException {
            return length;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            if (off < 0 || len < 0 || len+off > b.length) {
                throw new IndexOutOfBoundsException();
            }
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
            long k = Math.min(Math.max(n, 0), (long) length);
            offset += k;
            length -= k;
            return k;
        }
    }
}
