package com.cleo.labs.util.zip;

import static org.junit.Assert.*;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Date;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;

import org.junit.Ignore;
import org.junit.Test;

import com.cleo.labs.util.zip.MockBagOFiles.DirectoryVerifier;
import com.google.common.io.CountingInputStream;
import com.google.gwt.thirdparty.guava.common.io.ByteStreams;

public class TestZapDirectoryInputStream {

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
        //MockBagOFiles root = new MockBagOFiles().files("f%d.txt", 1, 50000, 10000, (byte)' ');
        MockBagOFiles root = new MockBagOFiles()
                .dirs("d%d", 1, 3)
                .dirs("e%d", 1, 3)
                .files("f%d.txt", 1, 10000, 10000, (byte)' ')
                .up()
                .files("e%d.txt", 1, 100, 100, (byte)'.');
        DirectoryVerifier verifier = root.verifier();
        try (ZipDirectoryInputStream zip = ZipDirectoryInputStream.builder(root.root())
                .opener(root.opener())
                .level(Deflater.NO_COMPRESSION)
                .build();
            ZipDirectoryOutputStream unzip = new ZipDirectoryOutputStream(p -> new File(PathUtil.join(p)))) {
           unzip.processor(entry -> {
                    if (!entry.directory()) {
                        OutputStream os = verifier.verify(entry.path());
                        assertNotNull("path not found or duplicate: "+entry.path().toString(), os);
                        return os;
                    }
                    return null;
                });
           CountingInputStream nzip = new CountingInputStream(zip);
           ByteStreams.copy(nzip,  unzip);
           nzip.close();
           unzip.flush();
           unzip.close();
           boolean verified = verifier.verified();
           if (!verified) {
               System.out.println(verifier.toString());
           }
           assertTrue(verified);
           System.out.println(nzip.getCount());
        }
    }

    @Ignore
    @Test
    public void testTotalSize() throws IOException {
        long totalSize = 0;
        MockBagOFiles root = new MockBagOFiles().files("f%d.txt", 1, 50000, 10000, (byte)' ');
        try (ZipDirectoryInputStream zip = ZipDirectoryInputStream.builder(root.root())
                .opener(root.opener())
                .level(Deflater.NO_COMPRESSION)
                .build()) {
            totalSize = zip.getTotalSize();
            //System.out.println("totalSize="+totalSize);
            assertTrue(zip.isTotalSizeCalculatedYet());
            assertEquals(totalSize, zip.getTotalSize());
        }
        DirectoryVerifier verifier = root.verifier();
        try (ZipDirectoryInputStream zip = ZipDirectoryInputStream.builder(root.root())
                .opener(root.opener())
                .level(Deflater.NO_COMPRESSION)
                .build();
            ZipDirectoryOutputStream unzip = new ZipDirectoryOutputStream(p -> new File(PathUtil.join(p)))) {
           unzip.processor(entry -> {
                    if (!entry.directory()) {
                        OutputStream os = verifier.verify(entry.path());
                        assertNotNull("path not found or duplicate: "+entry.path().toString(), os);
                        return os;
                    }
                    return null;
                });
            long copiedSize = ByteStreams.copy(zip,  unzip);
            assertEquals(totalSize, copiedSize);
            assertEquals(totalSize, zip.getCurrentSize());
        }
        /*
        String sandbox = PathUtil.join(System.getProperty("user.home"), "Downloads", "sand");
        int i = 0;
        while (new File(PathUtil.join(sandbox, String.valueOf(i))).exists()) i++;
        String testbox = PathUtil.join(sandbox, String.valueOf(i));
        ZipDirectoryOutputStream unzip = new ZipDirectoryOutputStream(p -> new File(PathUtil.append(testbox, p)));
        unzip.setProcessor((zip) -> {
            if (zip.path().length<=1) {
                //System.out.println("this is a root file: "+zip.path());
                return null;
            } else {
                return ZipDirectoryOutputStream.defaultProcessor.process(zip);
            }
        });
        unzip.setFilter(ZipDirectoryOutputStream.excluding("glob:.git/**","glob:{**}/*.class"));
        Files.copy(ZIP, unzip);
        unzip.close();
        */
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
