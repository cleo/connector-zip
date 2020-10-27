package com.cleo.labs.connector.zip;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Date;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;

import org.junit.Test;

import com.cleo.labs.util.zip.ZipDirectoryInputStream;
import com.cleo.labs.util.zip.ZipDirectoryOutputStream;

public class TestZipDirectoryInputStream {

    private static final java.nio.file.Path ZIP = Paths.get(System.getProperty("user.home"), "Downloads", "test.zip");

    @SuppressWarnings("unused")
    private static class NoisyFileInputStream extends FilterInputStream {

        protected NoisyFileInputStream(String filename) throws FileNotFoundException {
            super(new FileInputStream(filename));
        }

        @Override
        public int available() throws IOException {
            int result = super.available();
            System.err.println(String.format("available()=%d", result));
            return result;
        }

        @Override
        public int read(byte[] b, int off, int len) throws IOException {
            int result = super.read(b, off, len);
            System.err.println(String.format("read(b[%d],%d,%d)=%d", b.length, off, len, result));
            return result;
        }
        @Override
        public int read(byte[] b) throws IOException {
            int result = super.read(b);
            System.err.println(String.format("read(b[%d])=%d", b.length, result));
            return result;
        }
        @Override
        public int read() throws IOException {
            int result = super.read();
            System.err.println(String.format("read()=%d", result));
            return result;
        }
        @Override
        public long skip(long n) throws IOException {
            System.err.println(String.format("skip(%d)", n));
            return super.skip(n);
        }
        @Override
        public void close() throws IOException {
            System.err.println(String.format("close()"));
            super.close();
        }
        
    }

    @SuppressWarnings("unused")
    private String dumpEntry(ZipEntry entry) {
        StringBuilder s = new StringBuilder();
        s.append(entry.getName());
        if (entry.getSize()>=0) s.append(" size=").append(entry.getSize());
        if (entry.getCompressedSize()>=0) s.append(" csize=").append(entry.getCompressedSize());
        if (entry.getTime()>=0) s.append(" time=").append(new Date(entry.getTime()));
        return s.toString();
    }

    @Test
    public void test() throws IOException {
        long totalSize = 0;
        try (ZipDirectoryInputStream zip = ZipDirectoryInputStream.builder(Paths.get(".").toFile())
                .opener(f -> { return new FileInputStream(f.file); })
                .level(Deflater.NO_COMPRESSION)
                .build()) {
            totalSize = zip.getTotalSize();
            System.out.println("totalSize="+totalSize);
        }
        try (ZipDirectoryInputStream zip = ZipDirectoryInputStream.builder(Paths.get(".").toFile())
                .opener(f -> { return new FileInputStream(f.file); })
                .level(Deflater.NO_COMPRESSION)
                .build()) {
            Files.copy(zip, ZIP, StandardCopyOption.REPLACE_EXISTING);
            assertTrue(ZIP.toFile().exists());
            assertEquals(totalSize, ZIP.toFile().length());
            assertEquals(totalSize, zip.getCurrentSize());
        }
        Path sandbox = Paths.get(System.getProperty("user.home"), "Downloads", "sand");
        int i = 0;
        while (sandbox.resolve(String.valueOf(i)).toFile().exists()) i++;
        Path testbox = sandbox.resolve(String.valueOf(i));
        ZipDirectoryOutputStream unzip = new ZipDirectoryOutputStream(p -> testbox.resolve(p).toFile());
        unzip.setFilter(ZipDirectoryOutputStream.excluding("glob:.git/**","glob:**/*.class"));
        Files.copy(ZIP, unzip);
        unzip.close();
        /*
        ZipInputStream unzip = new ZipInputStream(new NoisyFileInputStream(ZIP));
        byte[] b = new byte[8192];
        int n;
        for (ZipEntry entry=unzip.getNextEntry(); entry!=null; entry=unzip.getNextEntry()) {
            System.err.println("unzip.getNextEntry()="+dumpEntry(entry));
            do {
                System.err.println(String.format("unzip.read(%d)", b.length));
                n = unzip.read(b);
                System.err.println(String.format("unzip.read(%d)=%d", b.length, n));
            } while (n >= 0);
            System.err.println("unzip.closeEntry() "+dumpEntry(entry));
            unzip.closeEntry();
        }
        System.err.println("unzip.close()");
        unzip.close();
        */
    }

}
