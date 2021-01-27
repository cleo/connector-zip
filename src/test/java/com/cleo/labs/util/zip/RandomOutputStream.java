package com.cleo.labs.util.zip;

import java.io.IOException;
import java.io.OutputStream;

public class RandomOutputStream extends OutputStream {
    private RandomInputStream input;
    private boolean error;

    public RandomOutputStream(long seed, long length) {
        input = new RandomInputStream(seed, length);
        error = false;
    }

    @Override
    public void write(int b) throws IOException {
        if (error) return;
        int c = input.read();
        if (c<0) {
            error = true;
            throw new IOException("write does not match expected random stream length (too long)");
        } else if ((b & 0xFF) != c) {
            error = true;
            throw new IOException("write does not match expected random stream value at "+input.offset()+" write("+b+") expected "+c);
        }
    }

    /**
     * Compares two byte array subsequences, returning 0 if they are equal, -1 if a<b,
     * and +1 if a>b.
     * @param a the array on the left
     * @param aPos the subsequence starting offset in a
     * @param b the array on the right
     * @param bPos the subsequence starting offset in b
     * @param length the number of bytes to compare
     * @return 0 for equal, -1 for a<b, +1 for a>b
     */
    private static int arraycompare(byte[] a, int aPos, byte[] b, int bPos, int length) {
        for (int i=0; i<length; i++) {
            if (a[aPos++] != b[bPos++]) {
                if (a[aPos-1]<b[bPos-1]) {
                    return -1;
                } else {
                    return 1;
                }
            }
        }
        return 0;
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        if (error) return;
        if (off<0 || len<0 || off+len>b.length) {
            throw new IndexOutOfBoundsException();
        }
        byte[] check = new byte[len];
        if (input.read(check)!=len) {
            error = true;
            throw new IOException("write does not match expected random stream length (too long)");
        } else if (arraycompare(b, off, check, 0, len)!=0) {
            error = true;
            throw new IOException("write does not match expected random stream value");
        }
    }
    
    @Override
    public void close() throws IOException {
        boolean eof = input.read()<0;
        super.close();
        input.close();
        if (!error && !eof) {
            error = true;
            throw new IOException("write does not match expected random stream length (too short)");
        }
    }
}