package com.cleo.labs.util.zip;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

public class RandomInputStream extends InputStream {
    private java.util.Random random;
    private long available;
    private byte[] slop;
    private int slops;
    private long length;

    /**
     * Returns the full length of the stream as configured
     * in the constructor.
     * @return the length
     */
    public long length() {
        return length;
    }

    /**
     * Returns the number of bytes read so far from the stream.
     * @return the offset between {@code 0} and {@code length()}.
     */
    public long offset() {
        return length-available;
    }

    /**
     * Create a random input stream with a specified length
     * and seed.  Note that a seed of 0 produces a sequence
     * of {@code length} null bytes {@code '\0'}.
     * @param seed the seed, or 0 for a stream of nulls
     * @param length the length of the stream
     */
    public RandomInputStream(long seed, long length) {
        if (seed>0) {
            random = new java.util.Random(seed);
        } else {
            random = null;
        }
        slop = new byte[4];
        slops = 0;
        available = length;
        this.length = length;
    }

    @Override
    public int read() throws IOException {
        if (available<=0L) return -1; // end of file
        available--;
        if (random==null)  return 0;  // /dev/zero emulation
        if (slops==0) {
            random.nextBytes(slop);
            slops = slop.length;
        }
        return slop[slop.length-slops--] & 0xFF;
    }

    @Override
    public int read(byte[] b, int off, int len) throws IOException {
        if (off<0 || len<0 || off+len>b.length) {
            throw new IndexOutOfBoundsException();
        }
        if (available<=0L) return -1; // end of file
        int n = len = Math.min(len, available());
        available -= n;
        if (random==null) { // /dev/zero emulation
            Arrays.fill(b, off, n, (byte)0);
        } else {
            if (slops==0 && off==0 && (len&3)==0 && len==b.length) {
                // if it is a perfect fit, let random do the work
                random.nextBytes(b);
            } else {
                // otherwise chunk it in 4 bytes at a time, modulo slop
                // note that off ratchets up while len ratchets down, but we have n to return
                while (len>0) {
                    if (slops==0) {
                        random.nextBytes(slop);
                        slops = slop.length;
                    }
                    int chunk = Math.min(len, slops);
                    System.arraycopy(slop, slop.length-slops, b, off, chunk);
                    off += chunk;
                    len -= chunk;
                    slops -= chunk;
                }
            }
        }
        return n;
    }

    @Override
    public int available() throws IOException {
        return (int)Math.min(available, (long)Integer.MAX_VALUE);
    }
}