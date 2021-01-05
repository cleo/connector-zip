package com.cleo.labs.util.zip;

import static org.junit.Assert.*;

import org.junit.Test;

import com.cleo.labs.util.zip.Finder.DirectoryMode;
import com.cleo.labs.util.zip.Found.Operation;

public class TestReplication {

    @Test
    public void testBaseline() {
        MockBagOFiles root = new MockBagOFiles()
                .dirs("d%d", 1, 3)
                .dirs("e%d", 1, 3)
                .files("f%d.txt", 1, 10, 10000, (byte)' ')
                .up()
                .files("e%d.txt", 1, 10, 100, (byte)'.')
                .files(".e%d.txt", 1, 10, 100, (byte)'.');
        Finder files = new Finder(root.root())
                .filter(Finder.excluding("glob:{**/,}.*"))
                .directoryMode(DirectoryMode.excludeEmpty);
        int dcount = 0;
        int fcount = 0;
        for (Found f : files) {
            if (f.directory()) {
                dcount++;
            } else {
                fcount++;
            }
        }
        assertEquals(13, dcount); // root and d[1-3]/ and d[1-3]/e[1-3]/
        assertEquals(120, fcount); // 10 f in each of 9 + 10 e in each of 3, skipping 30 . files
    }

    @Test
    public void testMissing() {
        MockBagOFiles root = new MockBagOFiles()
                .files("t%d", 1, 2, 100, (byte)'-')
                .dirs("d%d", 1, 3)
                .dirs("e%d", 1, 3)
                .files("f%d.txt", 1, 10, 10000, (byte)' ')
                .up()
                .files("e%d.txt", 1, 10, 100, (byte)'.')
                .files(".e%d.txt", 1, 10, 100, (byte)'.');
        Finder files = new Finder(root.root())
                .filter(Finder.excluding("glob:{**/,}.*"))
                .directoryMode(DirectoryMode.excludeEmpty);
        MockBagOFiles remote = new MockBagOFiles()
                .now(root.now())
                .dirs("d%d", 1, 3)
                .dirs("e%d", 1, 2) // missing /d[1-3]/e3 (3 directories)
                .files("f%d.txt", 1, 9, 10000, (byte)' ') // missing f10.txt in d[1-3]/e[12], plus f[1-10].txt in above 3
                .up()
                .files("e%d.txt", 1, 10, 100, (byte)'.');
        LocalFinderInputStream remoteListing = LocalFinderInputStream.builder(remote.root()).build();
        files.remoteReplica(remoteListing);
        int dcount = 0;
        int fcount = 0;
        int ncount = 0;
        for (Found f : files) {
            if (f == null) {
                ncount++;
                if (ncount > 1000) fail();
            } else if (f.operation() == Operation.match) {
            } else if (f.directory()) {
                //System.out.println(f);
                dcount++;
            } else {
                //System.out.println(f);
                fcount++;
            }
        }
        //System.out.println("dirs="+dcount+" files="+fcount);
        assertEquals(2+3*2*1+3*10, fcount); // t[12].txt, f10.txt in d[1-3]/e[12], plus f[1-10].txt in above 3
        assertEquals(3, dcount); // d[1-3]/e3
    }

    @Test
    public void testExtra() {
        MockBagOFiles root = new MockBagOFiles()
                .dirs("d%d", 1, 3)
                .dirs("e%d", 1, 2) // missing /d[1-3]/e3 (3 directories)
                .files("f%d.txt", 1, 9, 10000, (byte)' ') // missing f10.txt in d[1-3]/e[12], plus f[1-10].txt in above 3
                .up()
                .files("e%d.txt", 1, 10, 100, (byte)'.');
        Finder files = new Finder(root.root())
                .filter(Finder.excluding("glob:{**/,}.*"))
                .directoryMode(DirectoryMode.excludeEmpty);
        MockBagOFiles remote = new MockBagOFiles()
                .now(root.now())
                .files("t%d", 1, 2, 100, (byte)'-')
                .dirs("d%d", 1, 3)
                .dirs("e%d", 1, 3)
                .files("f%d.txt", 1, 10, 10000, (byte)' ')
                .up()
                .files("e%d.txt", 1, 10, 100, (byte)'.');
        LocalFinderInputStream remoteListing = LocalFinderInputStream.builder(remote.root()).build();
        files.remoteReplica(remoteListing);
        int dcount = 0;
        int fcount = 0;
        int ncount = 0;
        for (Found f : files) {
            if (f == null) {
                ncount++;
                if (ncount > 1000) fail();
            } else if (f.operation() == Operation.match) {
            } else if (f.directory()) {
                //System.out.println(f);
                dcount++;
            } else {
                //System.out.println(f);
                fcount++;
            }
        }
        //System.out.println("dirs="+dcount+" files="+fcount);
        assertEquals(0, fcount); // t[12].txt, f10.txt in d[1-3]/e[12], plus f[1-10].txt in above 3
        assertEquals(0, dcount); // d[1-3]/e3
    }

    @Test
    public void testExtraDeletes() {
        MockBagOFiles root = new MockBagOFiles()
                .dirs("d%d", 1, 3)
                .dirs("e%d", 1, 2) // missing /d[1-3]/e3 (3 directories)
                .files("f%d.txt", 1, 9, 10000, (byte)' ') // missing f10.txt in d[1-3]/e[12], plus f[1-10].txt in above 3
                .up()
                .files("e%d.txt", 1, 10, 100, (byte)'.');
        Finder files = new Finder(root.root())
                .filter(Finder.excluding("glob:{**/,}.*"))
                .directoryMode(DirectoryMode.excludeEmpty);
        MockBagOFiles remote = new MockBagOFiles()
                .now(root.now())
                .files("t%d", 1, 2, 100, (byte)'-')
                .dirs("d%d", 1, 3)
                .dirs("e%d", 1, 3)
                .files("f%d.txt", 1, 10, 10000, (byte)' ')
                .up()
                .files("e%d.txt", 1, 10, 100, (byte)'.');
        LocalFinderInputStream remoteListing = LocalFinderInputStream.builder(remote.root()).build();
        files.remoteReplica(remoteListing)
             .replicateDeletes(true);
        int dcount = 0;
        int fcount = 0;
        int ncount = 0;
        for (Found f : files) {
            if (f == null) {
                ncount++;
                if (ncount > 1000) fail();
            } else if (f.operation() == Operation.match) {
            } else if (f.directory()) {
                assertEquals(Operation.delete, f.operation());
                //System.out.println(f);
                dcount++;
            } else {
                assertEquals(Operation.delete, f.operation());
                //System.out.println(f);
                fcount++;
            }
        }
        //System.out.println("dirs="+dcount+" files="+fcount);
        assertEquals(2+3*2*1+3*10, fcount); // t[12].txt, f10.txt in d[1-3]/e[12], plus f[1-10].txt in above 3
        assertEquals(3, dcount); // d[1-3]/e3
    }

}
