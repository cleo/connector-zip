package com.cleo.labs.connector.zip;

import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.Deflater;

import org.junit.Test;

import com.cleo.connector.api.ConnectorClient;
import com.cleo.connector.api.command.ConnectorCommandResult;
import com.cleo.connector.api.command.ConnectorCommandResult.Status;
import com.cleo.connector.testing.Commands;
import com.cleo.connector.testing.StringSource;
import com.cleo.connector.testing.TestConnectorClientBuilder;
import com.cleo.labs.util.zip.MockBagOFiles;
import com.cleo.labs.util.zip.PathUtil;
import com.cleo.labs.util.zip.ZipDirectoryInputStream;
import com.cleo.labs.util.zip.ZipDirectoryOutputStream;
import com.cleo.labs.util.zip.MockBagOFiles.DirectoryVerifier;
import com.google.common.io.CountingInputStream;
import com.google.gwt.thirdparty.guava.common.io.ByteStreams;

public class TestPut {

    private ConnectorClient setupClient(FileFactory factory) throws Exception {
        TestConnectorClientBuilder builder = (TestConnectorClientBuilder) new TestConnectorClientBuilder(ZipConnectorSchema.class)
            .logger(System.err)
            .debug(true)
            .set("RootPath", "")
            .set("CompressionLevel", "none")
            .set("UnzipMode", "unzip")
            ;
        return (ConnectorClient) builder.build(factory);
    }

    private MockBagOFiles mockBag() {
        return new MockBagOFiles()
                .dirs("d%d", 1, 3)
                .dirs("e%d", 1, 3)
                .files("f%d.txt", 1, 100, 10000, (byte)' ')
                .up()
                .files("e%d.txt", 1, 100, 100, (byte)'.');
    }

    private InputStream mockZip(MockBagOFiles files) throws IOException {
        return ZipDirectoryInputStream.builder(files.root())
                .opener(files.opener())
                .level(Deflater.NO_COMPRESSION)
                .build();

    }

    @Test
    public void test() throws Exception {
        MockBagOFiles root = mockBag();
        DirectoryVerifier verifier = root.verifier();
        InputStream zip = mockZip(root);
        ConnectorClient client = setupClient(verifier.factory());
        ConnectorCommandResult result;
        StringSource source = new StringSource("all.zip", "") {
            @Override
            public InputStream getStream() {
                return zip;
            }
        };
        result = Commands.put(source, "").go(client);
        assertEquals(Status.Success, result.getStatus());
        boolean verified = verifier.verified();
        if (!verified) {
            System.out.println(verifier);
        }
        assertTrue(verified);
   }

    @Test
    public void hmm() throws Exception {
        MockBagOFiles root = mockBag();
        DirectoryVerifier verifier = root.verifier();
        InputStream zip = mockZip(root);
        ZipDirectoryOutputStream unzip = new ZipDirectoryOutputStream(p -> new File(PathUtil.join(p)));
        unzip.setProcessor(entry -> {
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
        assertTrue(verifier.verified());
    }

}
