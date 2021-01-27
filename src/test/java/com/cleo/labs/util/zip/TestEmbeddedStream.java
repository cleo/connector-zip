package com.cleo.labs.util.zip;

import static org.junit.Assert.*;

import java.io.IOException;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;

import org.junit.Test;

import com.google.common.io.ByteStreams;

public class TestEmbeddedStream {

    @Test
    public void test() throws IOException {
        PipedOutputStream pos = new PipedOutputStream();
        PipedInputStream pis = new PipedInputStream(pos);

        new Thread(() -> {
            try {
                EmbeddedOutputStream eos;

                eos = new EmbeddedOutputStream(pos);
                RandomInputStream ris1 = new RandomInputStream(1, 1024*1024L);
                ByteStreams.copy(ris1,  eos);
                ris1.close();
                eos.close();

                eos = new EmbeddedOutputStream(pos);
                RandomInputStream ris2 = new RandomInputStream(2, 1024*1024L);
                ByteStreams.copy(ris2,  eos);
                ris2.close();
                eos.close();

                pos.close();
            } catch (IOException e) {
                e.printStackTrace();
                fail();
            }
        }).start();

        EmbeddedInputStream eis;

        eis = new EmbeddedInputStream(pis);
        RandomOutputStream ros1 = new RandomOutputStream(1, 1024*1024L);
        ByteStreams.copy(eis, ros1);
        eis.close();
        ros1.close();

        eis = new EmbeddedInputStream(pis);
        RandomOutputStream ros2 = new RandomOutputStream(2, 1024*1024L);
        ByteStreams.copy(eis, ros2);
        eis.close();
        ros2.close();

        pis.close();
    }

}
