package com.cleo.labs.util.zip;

import static org.junit.Assert.*;

import java.util.stream.Stream;

import org.junit.Test;

import com.cleo.labs.util.zip.Found.Operation;

public class TestFound {

    @Test
    public void testCalculateReplicaSame() {
        Found local = new Found().directory(true).fullname("/").contents(new Found[] {
                new Found().directory(true).fullname("a/"),
                new Found().directory(true).fullname("c/"),
                new Found().directory(true).fullname("e/"),
            });
        Found replica = local.calculateReplica(local);
        assertEquals(local.contents().length, replica.contents().length);
        assertTrue(Stream.of(replica.contents()).allMatch(f -> f.operation()==Operation.match));

        local = new Found().directory(true).fullname("/").contents(new Found[] {
                new Found().directory(false).fullname("a").length(100).modified(4),
                new Found().directory(false).fullname("c").length(200).modified(26),
                new Found().directory(false).fullname("e").length(300).modified(42),
            });
        replica = local.calculateReplica(local);
        assertEquals(local.contents().length, replica.contents().length);
        assertTrue(Stream.of(replica.contents()).allMatch(f -> f.operation()==Operation.match));
    }

    @Test
    public void testCalculateReplicaFiles2Dirs() {
        Found local = new Found().directory(true).fullname("/").contents(new Found[] {
                new Found().directory(true).fullname("a/"),
                new Found().directory(true).fullname("c/"),
                new Found().directory(true).fullname("e/"),
            });
        Found remote = new Found().directory(true).fullname("/").contents(new Found[] {
                new Found().directory(false).fullname("a").length(100).modified(4),
                new Found().directory(false).fullname("c").length(200).modified(26),
                new Found().directory(false).fullname("e").length(300).modified(42),
            });
        Found replica = local.calculateReplica(remote);
        assertEquals(6, replica.contents().length);
        assertEquals(true, replica.contents()[0].directory());
        assertEquals("a/", replica.contents()[0].fullname());
        assertEquals(Operation.add, replica.contents()[0].operation());
        assertEquals(false, replica.contents()[3].directory());
        assertEquals("a", replica.contents()[3].fullname());
        assertEquals(Operation.delete, replica.contents()[3].operation());
    }

    @Test
    public void testCalculateReplicaOne() {
        Found local = new Found().directory(true).fullname("/").contents(new Found[] {
                new Found().directory(true).fullname("a/"),
                new Found().directory(false).fullname("c").length(201).modified(26),
                new Found().directory(false).fullname("e").length(300).modified(42),
            });
        Found remote = new Found().directory(true).fullname("/").contents(new Found[] {
                new Found().directory(true).fullname("a/"),
                new Found().directory(false).fullname("c").length(200).modified(26),
                new Found().directory(false).fullname("e").length(300).modified(42),
            });
        Found replica = local.calculateReplica(remote);
        assertEquals(3, replica.contents().length);
        assertEquals(Operation.match, replica.contents()[0].operation());
        assertEquals(Operation.add, replica.contents()[1].operation());
        assertEquals(false, replica.contents()[1].directory());
        assertEquals("c", replica.contents()[1].fullname());
        assertEquals(201, replica.contents()[1].length());
        assertEquals(Operation.match, replica.contents()[2].operation());
    }

    @Test
    public void testCalculateReplicaTwo() {
        Found local = new Found().directory(true).fullname("/").contents(new Found[] {
                new Found().directory(true).fullname("d1/e1/"),
                new Found().directory(true).fullname("d1/e2/"),
                new Found().directory(true).fullname("d1/e3/"),
                new Found().directory(false).fullname("d1/e1.txt").length(201).modified(26),
                new Found().directory(false).fullname("d1/e10.txt").length(210).modified(26),
                new Found().directory(false).fullname("d1/e2.txt").length(202).modified(26),
                new Found().directory(false).fullname("d1/e3.txt").length(203).modified(26),
            });
        Found remote = new Found().directory(true).fullname("/").contents(new Found[] {
                new Found().directory(true).fullname("d1/e1/"),
                new Found().directory(true).fullname("d1/e2/"),
                new Found().directory(false).fullname("d1/e1.txt").length(201).modified(26),
                new Found().directory(false).fullname("d1/e10.txt").length(210).modified(26),
                new Found().directory(false).fullname("d1/e2.txt").length(202).modified(26),
                new Found().directory(false).fullname("d1/e3.txt").length(203).modified(26),
            });
        Found replica = local.calculateReplica(remote);
        assertEquals(7, replica.contents().length);
        assertEquals(Operation.match, replica.contents()[0].operation());
        assertEquals(Operation.match, replica.contents()[1].operation());
        assertEquals(Operation.add, replica.contents()[2].operation());
        assertEquals(Operation.match, replica.contents()[3].operation());
        assertEquals(Operation.match, replica.contents()[4].operation());
        assertEquals(Operation.match, replica.contents()[5].operation());
        assertEquals(Operation.match, replica.contents()[6].operation());
    }

    @Test
    public void testCalculateReplicaAddAll() {
        Found local = new Found().directory(true).fullname("/").contents(new Found[] {
                new Found().directory(true).fullname("a/"),
                new Found().directory(true).fullname("c/"),
                new Found().directory(true).fullname("e/"),
                new Found().directory(false).fullname("a").length(100).modified(4),
                new Found().directory(false).fullname("c").length(200).modified(26),
                new Found().directory(false).fullname("e").length(300).modified(42),
            });
        Found remote = new Found().directory(true).fullname("/").contents(new Found[] {
            });
        Found replica = local.calculateReplica(remote);
        assertEquals(local.contents().length, replica.contents().length);
        for (int i=0; i<local.contents().length; i++) {
            assertEquals(Operation.add, replica.contents()[i].operation());
            assertEquals(local.contents()[i].directory(), replica.contents()[i].directory());
            assertEquals(local.contents()[i].fullname(), replica.contents()[i].fullname());
            if (!replica.contents()[i].directory()) {
                assertEquals(local.contents()[i].length(), replica.contents()[i].length());
                assertEquals(local.contents()[i].modified(), replica.contents()[i].modified());
            }
        }
    }
}
