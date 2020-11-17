package com.cleo.labs.connector.zip;

import static org.junit.Assert.assertEquals;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Arrays;

import org.junit.Ignore;
import org.junit.Test;

import com.cleo.labs.util.zip.LambdaWriterInputStream;

public class TestLambdaWriterInputStream {

    public static class TestWriter implements LambdaWriterInputStream.Writer {
        private int i;
        private int limit;
        public TestWriter(int limit) {
            this.i = 1;
            this.limit = limit;
        }
        @Override
        public void write(OutputStream o) {
            byte[] b = new byte[i];
            Arrays.fill(b, (byte)('a'+i-1));
            try {
                System.err.println("writing "+i+" "+(char)('a'+i-1)+"s.");
                o.write(b);
                i++;
                if (i > limit) {
                    o.close();
                }
            } catch (IOException ignore) {}
        }
    }

    private String getChars(InputStream in, int n) {
        byte[] b = new byte[n];
        try {
            n = in.read(b);
        } catch (IOException ignore) {}
        return new String(b, 0, n);
    }

    @Ignore
    @Test
    public void test() throws IOException {
        LambdaWriterInputStream cos = new LambdaWriterInputStream(new TestWriter(8), 20);
        InputStream is = cos;
        assertEquals("abbcccddddeeeee", getChars(is, 15));
        assertEquals("ffffffggggggghhhhhhh", getChars(is, 20));
        assertEquals("h", getChars(is, 20));
        cos.close();
    }

}
