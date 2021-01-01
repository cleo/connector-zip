package com.cleo.labs.util.zip;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class FillInputStream extends InputStream {

    private byte fill;
    private long limit;
    private long count;

    public FillInputStream(byte fill) {
        this(fill, -1L);
    }

    public FillInputStream(byte fill, long limit) {
        this.fill = fill;
        this.limit = limit;
        this.count = 0L;
    }

    @Override
    public int read() throws IOException {
        if (limit >= 0 && count >= limit) {
            return -1;
        }
        count++;
        return fill;
    }

    @Override
    public int read(byte[] b) throws IOException {
        return read(b, 0, b.length);
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (limit >= 0) {
            if (count >= limit) {
                return -1;
            } else if (count+len > limit) {
                len = (int)(limit-count);
            }
        }
        Arrays.fill(b, off, len, fill);
        count += len;
        return len;
    }
}
