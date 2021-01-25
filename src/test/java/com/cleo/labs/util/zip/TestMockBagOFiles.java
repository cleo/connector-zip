package com.cleo.labs.util.zip;

import static org.junit.Assert.*;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.PushbackInputStream;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipInputStream;

import org.junit.Ignore;
import org.junit.Test;

import com.cleo.labs.util.zip.Finder.DirectoryMode;
import com.cleo.labs.util.zip.MockBagOFiles.DirectoryVerifier;
import com.cleo.labs.util.zip.PartitionedZipDirectory.Partition;
import com.google.common.io.ByteStreams;

public class TestMockBagOFiles {

    @Test
    public void testMock() throws IOException {
        long size = 0;
        MockBagOFiles root = new MockBagOFiles()
                .dirs("d%d", 1, 3)
                .dirs("e%d", 1, 3)
                .files("f%d.txt", 1, 10, 10000, (byte)' ')
                .up()
                .files("e%d.txt", 1, 10, 100, (byte)'.');
        DirectoryVerifier verifier = root.verifier();
        try (ZipDirectoryInputStream zip = ZipDirectoryInputStream.builder(root.root())
                .opener(root.opener())
                .filter(Finder.excluding("glob:.git/**","glob:**/*.class"))
                .directoryMode(DirectoryMode.exclude)
                .build();
             ZipDirectoryOutputStream unzip = new ZipDirectoryOutputStream((p -> new File(PathUtil.join(p))))) {
            unzip.processor(entry -> {
                     if (!entry.directory()) {
                         OutputStream os = verifier.verify(entry.path());
                         assertNotNull("path not found or duplicate: "+entry.path().toString(), os);
                         return os;
                     }
                     return null;
                 });
            assertNotNull(zip);
            assertNotNull(unzip);
            size = ByteStreams.copy(zip, unzip);
            zip.close();
            unzip.close();
            assertTrue(verifier.verified());
        }
        PartitionedZipDirectory zip = PartitionedZipDirectory.builder(root.root())
                .opener(root.opener())
                .filter(Finder.excluding("glob:.git/**","glob:**/*.class"))
                .threshold(0)
                .directoryMode(DirectoryMode.exclude)
                .build();
        List<Partition> partitions = zip.partitions();
        assertEquals(1, partitions.size());
        assertEquals(size, partitions.get(0).size());
    }

    @Ignore
    @Test
    public void testMockFinder() throws IOException {
        MockBagOFiles root = new MockBagOFiles()
                .dirs("d%d", 1, 3)
                .dirs("e%d", 1, 3)
                .files("f%d.txt", 1, 10, 10000, (byte)' ')
                .up()
                .files("e%d.txt", 1, 10, 100, (byte)'.');
        for (Found found : new Finder(root.root())) {
            System.out.println(found.fullname()+" -> "+found.file().getPath()+": "+found.file().length());
        }
    }

    @Test
    public void testMockExtra() throws IOException {
        MockBagOFiles root = new MockBagOFiles()
                .dirs("d%d", 1, 3)
                .dirs("e%d", 1, 3)
                .files("f%d.txt", 1, 10, 10000, (byte)' ')
                .up()
                .files("e%d.txt", 0, 11, 100, (byte)'.'); // extra e0.txt
        DirectoryVerifier verifier = new MockBagOFiles()
                .dirs("d%d", 1, 3)
                .dirs("e%d", 1, 3)
                .files("f%d.txt", 1, 10, 10000, (byte)' ')
                .up()
                .files("e%d.txt", 1, 10, 100, (byte)'.')
                .verifier();
        try (ZipDirectoryInputStream zip = ZipDirectoryInputStream.builder(root.root())
                .opener(root.opener())
                .filter(Finder.excluding("glob:.git/**","glob:**/*.class"))
                .directoryMode(DirectoryMode.exclude)
                .build();
             ZipDirectoryOutputStream unzip = new ZipDirectoryOutputStream((p -> new File(PathUtil.join(p))))) {
            unzip.processor(entry -> {
                     if (!entry.directory()) {
                         OutputStream os = verifier.verify(entry.path());
                         if (os==null) {
                             throw new IOException("path not found or duplicate: "+PathUtil.join(entry.path()));
                         }
                         return os;
                     }
                     return null;
                 });
            ByteStreams.copy(zip, unzip);
            zip.close();
            unzip.close();
            fail("should have thrown an exception");
        } catch (IOException ioe) {
            assertEquals("path not found or duplicate: d1/e0.txt", ioe.getMessage());
        }
    }

    @Test
    public void testMockMissing() throws IOException {
        MockBagOFiles root = new MockBagOFiles()
                .dirs("d%d", 1, 3)
                .dirs("e%d", 1, 3)
                .files("f%d.txt", 1, 10, 10000, (byte)' ')
                .up()
                .files("e%d.txt", 2, 9, 100, (byte)'.'); // missing e1.txt
        DirectoryVerifier verifier = new MockBagOFiles()
                .dirs("d%d", 1, 3)
                .dirs("e%d", 1, 3)
                .files("f%d.txt", 1, 10, 10000, (byte)' ')
                .up()
                .files("e%d.txt", 1, 10, 100, (byte)'.')
                .verifier();
        try (ZipDirectoryInputStream zip = ZipDirectoryInputStream.builder(root.root())
                .opener(root.opener())
                .filter(Finder.excluding("glob:.git/**","glob:**/*.class"))
                .directoryMode(DirectoryMode.exclude)
                .build();
             ZipDirectoryOutputStream unzip = new ZipDirectoryOutputStream((p -> new File(PathUtil.join(p))))) {
            unzip.processor(entry -> {
                     if (!entry.directory()) {
                         OutputStream os = verifier.verify(entry.path());
                         assertNotNull("path not found or duplicate: "+entry.path().toString(), os);
                         return os;
                     }
                     return null;
                 });
            ByteStreams.copy(zip, unzip);
            zip.close();
            unzip.close();
            assertTrue(!verifier.verified());
        }
    }

    public static final byte[] ZIP_SIGNATURE = new byte[] {0x50, 0x4B, 0x03, 0x04};
    public static final byte[] CLEO_SIGNATURE = new byte[] {0x0C, 0x4C, 0x0E, 0x00};
    public enum ArchiveType {zip, cleo, bytes};

    public ArchiveType inspect(PushbackInputStream in) throws IOException {
        byte[] sig = new byte[4];
        ByteStreams.readFully(in, sig);
        ArchiveType result = ArchiveType.bytes;
        if (Arrays.equals(sig, ZIP_SIGNATURE)) {
            result = ArchiveType.zip;
        } else if (Arrays.equals(sig, CLEO_SIGNATURE)) {
            result = ArchiveType.cleo;
        }
        in.unread(sig);
        return result;
    }

    @Test
    public void testSomeStuff() throws IOException {
        PushbackInputStream bytes = new PushbackInputStream(new FillInputStream((byte)' ', 100), 4);
        assertEquals(ArchiveType.bytes, inspect(bytes));
        PushbackInputStream cleo = new PushbackInputStream(new ByteArrayInputStream(CLEO_SIGNATURE), 4);
        assertEquals(ArchiveType.cleo, inspect(cleo));
        PushbackInputStream zip = new PushbackInputStream(new FileInputStream(System.getProperty("user.home")+"/d/vagrant/harmony/test.zip"), 4);
        assertEquals(ArchiveType.zip, inspect(zip));
        ZipInputStream unzip = new ZipInputStream(zip);
        assertNotNull(unzip.getNextEntry());
    }
}
