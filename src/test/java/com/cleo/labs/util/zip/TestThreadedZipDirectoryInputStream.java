package com.cleo.labs.util.zip;

import static org.junit.Assert.*;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Date;
import java.util.zip.Deflater;
import java.util.zip.ZipEntry;

import org.junit.Test;

import com.cleo.labs.util.zip.MockBagOFiles.DirectoryVerifier;
import com.google.common.io.ByteStreams;
import com.google.common.io.CountingInputStream;

public class TestThreadedZipDirectoryInputStream {

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
        MockBagOFiles root = new MockBagOFiles()
                .dirs("d%d", 1, 3)
                .dirs("e%d", 1, 3)
                .files("f%d.txt", 1, 10000, 10000, (byte)' ')
                .up()
                .files("e%d.txt", 1, 100, 100, (byte)'.');
        DirectoryVerifier verifier = root.verifier();
        try (InputStream zip = ThreadedZipDirectoryInputStream.builder(root.root())
                .copier((from,to) -> {
                        InputStream is = root.opener().open(from);
                        ByteStreams.copy(is, to);
                    })
                .level(Deflater.NO_COMPRESSION)
                .build();
            ZipDirectoryOutputStream unzip = new ZipDirectoryOutputStream(p -> Paths.get("", p).toFile())) {
           unzip.processor(entry -> {
                    if (!entry.directory()) {
                        //System.out.println("verify("+entry+")");
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

    @Test
    public void testFiles() throws IOException {
        Path root = Paths.get(System.getProperty("user.home"), "d","vagrant","cache","zip");
        MockBagOFiles rootv = new MockBagOFiles().files("f%d.txt", 1, 50000, 10000, (byte)' ');
        DirectoryVerifier verifier = rootv.verifier();
        try (InputStream zip = ThreadedZipDirectoryInputStream.builder(root.toFile())
                .copier((from,to) -> {
                    try (FileInputStream fis = new FileInputStream(from.file());
                            FileChannel channel = fis.getChannel()) {
                        ByteBuffer buffer = ByteBuffer.allocate(ThreadedZipDirectoryInputStream.DEFAULT_BUFFERSIZE);
                        int n;
                        while ((n = channel.read(buffer)) >= 0) {
                            if (n > 0) {
                                buffer.flip();
                                to.write(buffer.array(), buffer.arrayOffset(), buffer.remaining());
                            }
                            buffer.clear();
                        }
                    }
                    })
                .level(Deflater.NO_COMPRESSION)
                .build();
            ZipDirectoryOutputStream unzip = new ZipDirectoryOutputStream(p -> Paths.get("", p).toFile())) {
           unzip.processor(entry -> {
                    if (!entry.directory()) {
                        //System.out.println("verify("+entry+")");
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

    @Test
    public void testFilesNoThread() throws IOException {
        Path root = Paths.get(System.getProperty("user.home"), "d","vagrant","cache","zip");
        MockBagOFiles rootv = new MockBagOFiles().files("f%d.txt", 1, 50000, 10000, (byte)' ');
        DirectoryVerifier verifier = rootv.verifier();
        try (InputStream zip = ZipDirectoryInputStream.builder(root.toFile())
                .opener(f -> new FileInputStream(f.file()))
                .level(Deflater.NO_COMPRESSION)
                .build();
            ZipDirectoryOutputStream unzip = new ZipDirectoryOutputStream(p -> Paths.get("", p).toFile())) {
           unzip.processor(entry -> {
                    if (!entry.directory()) {
                        //System.out.println("verify("+entry+")");
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

    /*-----------*
     * ZAP TESTS *
     *-----------*/

    @Test
    public void testZap() throws IOException {
        MockBagOFiles root = new MockBagOFiles()
                .dirs("d%d", 1, 3)
                .dirs("e%d", 1, 3)
                .files("f%d.txt", 1, 10000, 10000, (byte)' ')
                .up()
                .files("e%d.txt", 1, 100, 100, (byte)'.');
        DirectoryVerifier verifier = root.verifier();
        try (InputStream zip = ThreadedZipDirectoryInputStream.builder(root.root())
                .copier((from,to) -> {
                        InputStream is = root.opener().open(from);
                        ByteStreams.copy(is, to);
                        is.close();
                        to.flush();
                    })
                .level(ZapFoundOutputStream.ZAP_LEVEL)
                .build();
            ZipDirectoryOutputStream unzip = new ZipDirectoryOutputStream(p -> Paths.get("", p).toFile())) {
           unzip.processor(entry -> {
                    if (!entry.directory()) {
                        //System.out.println("verify("+entry+")");
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

    @Test
    public void testZapFiles() throws IOException {
        Path root = Paths.get(System.getProperty("user.home"), "d","vagrant","cache","zip");
        MockBagOFiles rootv = new MockBagOFiles().files("f%d.txt", 1, 50000, 10000, (byte)' ');
        DirectoryVerifier verifier = rootv.verifier();
        try (InputStream zip = ThreadedZipDirectoryInputStream.builder(root.toFile())
                .copier((from,to) -> {
                    try (FileInputStream fis = new FileInputStream(from.file());
                            FileChannel channel = fis.getChannel()) {
                        ByteBuffer buffer = ByteBuffer.allocate(ThreadedZipDirectoryInputStream.DEFAULT_BUFFERSIZE);
                        int n;
                        while ((n = channel.read(buffer)) >= 0) {
                            if (n > 0) {
                                buffer.flip();
                                to.write(buffer.array(), buffer.arrayOffset(), buffer.remaining());
                            }
                            buffer.clear();
                        }
                    }
                    })
                .level(ZapFoundOutputStream.ZAP_LEVEL)
                .build();
            ZipDirectoryOutputStream unzip = new ZipDirectoryOutputStream(p -> Paths.get("", p).toFile())) {
           unzip.processor(entry -> {
                    if (!entry.directory()) {
                        //System.out.println("verify("+entry+")");
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

    @Test
    public void testZapFilesNoThread() throws IOException {
        Path root = Paths.get(System.getProperty("user.home"), "d","vagrant","cache","zip");
        MockBagOFiles rootv = new MockBagOFiles().files("f%d.txt", 1, 50000, 10000, (byte)' ');
        DirectoryVerifier verifier = rootv.verifier();
        try (InputStream zip = ZipDirectoryInputStream.builder(root.toFile())
                .opener(f -> new FileInputStream(f.file()))
                .level(ZapFoundOutputStream.ZAP_LEVEL)
                .build();
            ZipDirectoryOutputStream unzip = new ZipDirectoryOutputStream(p -> Paths.get("", p).toFile())) {
           unzip.processor(entry -> {
                    if (!entry.directory()) {
                        //System.out.println("verify("+entry+")");
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
}
