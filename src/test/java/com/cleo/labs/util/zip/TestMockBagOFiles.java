package com.cleo.labs.util.zip;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

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
             ZipDirectoryOutputStream unzip = new ZipDirectoryOutputStream(p -> p.toFile())) {
            unzip.setProcessor(entry -> {
                     if (!entry.entry().isDirectory()) {
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
             ZipDirectoryOutputStream unzip = new ZipDirectoryOutputStream(p -> p.toFile())) {
            unzip.setProcessor(entry -> {
                     if (!entry.entry().isDirectory()) {
                         OutputStream os = verifier.verify(entry.path());
                         if (os==null) {
                             throw new IOException("path not found or duplicate: "+entry.path().toString());
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
             ZipDirectoryOutputStream unzip = new ZipDirectoryOutputStream(p -> p.toFile())) {
            unzip.setProcessor(entry -> {
                     if (!entry.entry().isDirectory()) {
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

}
