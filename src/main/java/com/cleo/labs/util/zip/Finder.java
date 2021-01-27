package com.cleo.labs.util.zip;

import java.io.File;
import java.io.InputStream;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.PathMatcher;
import java.nio.file.Paths;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.Iterator;
import java.util.NoSuchElementException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.cleo.labs.util.zip.Found.Operation;

public class Finder implements Iterator<Found>, Iterable<Found> {

    public enum DirectoryMode {include, exclude, excludeEmpty, only};

    private enum State {
        /**
         * indicates an unstarted iterator where settings can still be changed.
         */
        NEW,
        /**
         * indicates an active iterator where the next element needs to be retrieved.
         */
        GET,
        /**
         * indicates an active iterator with the next element cached in {@code next}.
         */
        GOT,
        /**
         * indicates a completed iterator with no next element.
         */
        DONE}

    private State state;
    private Found next;
    private Found holdNext;
    private State holdState;
    private Deque<Found> holdDirectories;
    private LinkedBlockingDeque<Found> stack;
    private Deque<Found> pendingDirectories;
    private ArrayList<Found> checkpoint;
    private int count;

    private RemoteFinderStreamDecoder remoteDecoder;
    private boolean replicateDeletes;
    private long timeout;
    private TimeUnit unit;
    private ConcurrentMap<String,Found> remoteDirectories;
    private boolean decoderRunning;
    private boolean closed;

    private File root;
    private Predicate<Found> filter;
    private DirectoryMode directoryMode;
    private int[] restart;
    private int limit;

    private Consumer<String> debug;

    private boolean replicating() {
        return remoteDecoder != null;
    }

    private void start() {
        state = State.GET;
        Found start = new Found(new String[0], root, -1, 0);
        if (start.directory()) {
            if (replicating()) {
                start.operation(Operation.match);
                remoteDirectories = new ConcurrentHashMap<>();
                decoderRunning = true;
                new Thread(() -> {
                    for (Found remote : remoteDecoder) {
                        debug.accept("remote directory "+remote+" retrieved");
if (remote.contents()!=null) {
    Stream.of(remote.contents()).forEach(x->debug.accept("> "+x));
}
                        if (closed) {
                            debug.accept("remote directory listing canceled: closed");
                            remoteDecoder.close();
                            return;
                        }
                        Found local = remoteDirectories.putIfAbsent(remote.fullname(), remote);
                        if (local != null) {
                            if (local.remote()) {
                                throw new AssertionError("directory "+local.fullname()+" is not marked local");
                            }
if (local.contents()!=null) {
    Stream.of(local.contents()).forEach(x->debug.accept("< "+x));
}
                            Found dir = local.calculateReplica(remote);
if (dir.contents()!=null) {
    debug.accept("replica calulated from remote for "+dir);
    Stream.of(dir.contents()).forEach(x->debug.accept("= "+x));
}
                            remoteDirectories.remove(remote.fullname());
                            addToStack(dir, 0);
                        }
                    }
                    remoteDecoder.close();
                    debug.accept("remote directory listing completed");
                    if (remoteDecoder.exception()!=null) {
                        debug.accept("remote directory listing exception: "+remoteDecoder.exception().toString());
                    }
                    decoderRunning = false;
                    stack.addFirst(Found.FOUND_END);
                }, "decoderThread")
                .start();
                push(start);
            } else {
                stack.addFirst(start);
            }
        } else {
            stack.addFirst(start);
        }
    }

    private Found advance() {
        Found result = null;
        while (result==null && (!stack.isEmpty() || replicating() && decoderRunning)) {
            // pull the next Found, taking care of null (waiting for decodeThread)
            // and the DONE_FINDING sentinel
            try {
                result = stack.pollFirst(timeout, unit);
                if (result == Found.FOUND_END) {
                    result = null;
                }
            } catch (InterruptedException e) {
                result = null;
            }
            if (result != null) {
                // see if we need to report out a pendingDirectory,
                // making sure to put result back if we do!
                if (!pendingDirectories.isEmpty()) {
                    // throw out already bypassed parents
                    while (!pendingDirectories.isEmpty() && !pendingDirectories.peekLast().contains(result)) {
                        // pendingDirectories.peekLast() doesn't enclose peek so remove it
                        pendingDirectories.removeLast();
                    }
                    if (!result.directory() && !pendingDirectories.isEmpty()) {
                        // if we are peeking at a file, short-circuit the stack while we report out the parents
                        stack.addFirst(result); // put it back!
                        return pendingDirectories.pollFirst();
                    }
                }
                push(result);
                if (result.directory()) {
                    if (directoryMode == DirectoryMode.exclude) {
                        // don't report out this Found ever
                        result = null;
                    } else if (directoryMode == DirectoryMode.excludeEmpty &&
                            result.operation()!=Operation.delete &&
                            !result.containsAFile()) {
                        // don't report out this Found right now
                        pendingDirectories.add(result);
                        result = null;
                    }
                }
            }
        }
        return result;
    }

    private void addToStack(Found dir, int start) {
        Found[] found = dir.contents();
        for (int i=found.length-1; i>=start; i--) {
            if (directoryMode == DirectoryMode.only && !found[i].directory()) {
                // skip it
            } else if (!found[i].directory() && found[i].operation() == Operation.match) {
                // don't stack "match" files (but DO stack "match" directories for analysis)
            } else if (found[i].operation() == Operation.delete && !replicateDeletes) {
                // also don't stack "delete"s unless requested
            } else {
                stack.addFirst(found[i]);
            }
        }
    }

    private void push(Found dir) {
        // keep track of the state stack, where to pick up next
        while (checkpoint.size() > dir.depth()+1) {
            checkpoint.remove(checkpoint.size()-1);
        }
        checkpoint.add(dir);
        // if it's a directory, push more onto the todo stack
        if (dir.directory() && filter.test(dir)) {
            if (dir.file() != null) { 
                // filter, index and populate dir.contents
                File[] files = dir.file().listFiles();
                Found[] found = Stream.of(files)
                        .map(dir::child)
                        .filter(filter)
                        .sorted() // directories < files, otherwise compare fullname
                        .map(new Function<Found,Found> () {
                            private int i = 0;
                            @Override
                            public Found apply(Found t) {
                                t.index(i++)
                                 .operation(Operation.add);
                                return t;
                            }
                        })
                        .toArray(Found[]::new);
                dir.contents(found);
            } else if (dir.contents()==null) {
                // some deleted remote directories will not have contents
                dir.contents(new Found[0]);
            }
            // in replication mode, we have to compare any "match" directories
            // we can go ahead and push "add" and "delete"
            if (replicating() && dir.operation()==Operation.match) {
                Found remote = remoteDirectories.putIfAbsent(dir.fullname(), dir);
                if (remote == null) {
                    // if the remote decoder hasn't posted this one yet, wait for later
                    return;
                }
                if (!remote.remote()) {
                    throw new AssertionError("directory "+remote.fullname()+" is not marked remote");
                }
                remoteDirectories.remove(dir.fullname());
                dir = dir.calculateReplica(remote);
if (dir.contents()!=null) {
    debug.accept("replica calulated in push for "+dir);
    Stream.of(dir.contents()).forEach(x->debug.accept("= "+x));
}
            }
            // if we are restarting, prune the list by the restart index
            // remember the list is still backwards, so we just adjust the stop index
            // >>> note: restarting by index and by replication are not compatible <<<
            int start = 0;
            if (dir.depth() < restart.length-1) {
                start = restart[dir.depth()+1];
                restart[dir.depth()+1] = 0;
            }
            // now push them, in reverse to preserve order
            addToStack(dir, start);
        }
    }

    private boolean done() {
        if (limit > 0 && count >= limit) {
            return true;
        } else if (next != null) {
            return false;
        } else if (!replicating()) {
            return true;
        } else if (decoderRunning) {
            return false;
        } else if (!stack.isEmpty()) {
            return false;
        } else {
            if (!remoteDirectories.isEmpty()) {
                Collection<Found> values = remoteDirectories.values();
                for (Found dir : values) {
                    remoteDirectories.remove(dir.fullname());
                    if (dir.remote()) {
                        // orphaned remote listings get turned into "deletes"
                        if (replicateDeletes) {
                            dir.operation(Operation.delete);
                            if (dir.contents() != null) {
                                Stream.of(dir.contents()).forEach(f -> f.operation(Operation.delete));
                            }
                            push(dir);
                        }
                    } else {
                        // orphaned local listings get turned into "adds" (they are already)
                        dir.operation(Operation.add);
                        if (dir.contents() != null) {
                            Stream.of(dir.contents()).forEach(f -> f.operation(Operation.add));
                        }
                        push(dir);
                    }
                }
            }
            if (remoteDecoder.exception() != null) {
                throw new AssertionError("wrapped IOException found", remoteDecoder.exception());
            }
            return stack.isEmpty();
        }
    }

    private boolean get() {
        if (state == State.NEW) {
            start();
        }
        if (state == State.GET) { 
            if (limit > 0 && count >= limit) {
                next = null;
            } else {
                next = advance();
            }
            if (done()) {
                state = State.DONE;
                checkpoint.clear();
            } else {
                state = State.GOT;
                if (next != null) {
                    count++;
                }
            }
        }
        return state == State.GOT;
    }

    public Finder(File root) {
        this.root = root;
        this.filter = ALL;
        this.directoryMode = DirectoryMode.include;

        this.next = null;
        this.state = State.NEW;
        this.holdNext = null;
        this.holdState = State.NEW;
        this.holdDirectories = new ArrayDeque<>();
        this.stack = new LinkedBlockingDeque<>();
        this.pendingDirectories = new ArrayDeque<>();
        this.checkpoint = new ArrayList<>();
        this.restart = new int[0];
        this.limit = 0;
        this.count = 0;

        this.remoteDecoder = null;
        this.replicateDeletes = false;
        this.timeout = 10;
        this.unit = TimeUnit.SECONDS;
        this.decoderRunning = false;
        this.closed = false;

        this.debug = s->{};
    }

    public Finder filter(Predicate<Found> filter) {
        if (state != State.NEW) {
            throw new IllegalStateException("Finder is iterating -- can't set filter");
        }
        this.filter = filter;
        return this;
    }

    public Finder directoryMode(DirectoryMode directoryMode) {
        if (state != State.NEW) {
            throw new IllegalStateException("Finder is iterating -- can't set directoryMode");
        }
        this.directoryMode = directoryMode;
        return this;
    }

    public Finder restart(int[] restart) {
        if (state != State.NEW) {
            throw new IllegalStateException("Finder is iterating -- can't set restart");
        }
        this.restart = restart==null ? new int[0] : restart.clone();
        return this;
    }

    public Finder replicateDeletes(boolean replicateDeletes) {
        this.replicateDeletes = replicateDeletes;
        return this;
    }

    public Finder limit(int limit) {
        this.limit = limit;
        return this;
    }

    public Finder remoteReplica(InputStream remoteReplicaInputStream) {
        if (state != State.NEW) {
            throw new IllegalStateException("Finder is iterating -- can't start replicating");
        }
        this.remoteDecoder = new RemoteFinderStreamDecoder(remoteReplicaInputStream);
        return this;
    }

    public Finder timeout(long timeout, TimeUnit unit) {
        this.timeout = timeout;
        this.unit = unit;
        return this;
    }

    public Finder debug(Consumer<String> debug) {
        if (debug==null) {
            this.debug = s->{};
        } else {
            this.debug = debug;
        }
        return this;
    }

    public void hold() {
        hasNext(); // want to hold in GOT state so checkpoint is where to resume
        this.limit = count;
        holdNext = next;
        holdState = state;
        if (directoryMode != DirectoryMode.exclude && !checkpoint.isEmpty()) {
            holdDirectories = new ArrayDeque<>();
            holdDirectories.addAll(checkpoint.subList(0, checkpoint.size()-1));
        }
        next = null;
        state = State.DONE;
    }

    public void unhold() {
        this.limit = 0;
        this.count = holdState==State.GOT ? 1 : 0;
        next = holdNext;
        state = holdState;
        if (directoryMode != DirectoryMode.exclude) {
            pendingDirectories = holdDirectories;
            holdDirectories = new ArrayDeque<>();
        }
        holdNext = null;
    }

    public int count() {
        return count - ((holdNext!=null ? holdState : state)==State.GOT ? 1 : 0); // subtract the lookahead if in GOT state
    }

    @Override
    public boolean hasNext() {
        return get();
    }

    @Override
    public Found next() {
        if (!get()) {
            throw new NoSuchElementException();
        }
        state = State.GET;
        return next;
    }

    public int[] checkpoint() {
        if (checkpoint.size() <= 1) {
            return new int[0];
        }
        int[] indices = new int[checkpoint.size()-1];
        for (int i=1; i<checkpoint.size(); i++) {
            indices[i-1] = checkpoint.get(i).index();
        }
        return indices;
    }

    public static Predicate<Found> ALL = found->true;

    public static Predicate<Found> NONE = found->false;

    public static Predicate<Found> excluding(String...patterns) {
        if (patterns==null || patterns.length==0) {
            return ALL;
        }
        PathMatcher[] matchers = new PathMatcher[patterns.length];
        FileSystem fs = FileSystems.getDefault();
        for (int i=0; i<patterns.length; i++) {
            matchers[i] = fs.getPathMatcher(patterns[i]);
        }
        return f->!Stream.of(matchers)
            .anyMatch(m -> m.matches(Paths.get("", f.path())));
    }

    public static Predicate<Found> including(String...patterns) {
        if (patterns==null || patterns.length==0) {
            return NONE;
        }
        PathMatcher[] matchers = new PathMatcher[patterns.length];
        FileSystem fs = FileSystems.getDefault();
        for (int i=0; i<patterns.length; i++) {
            matchers[i] = fs.getPathMatcher(patterns[i]);
        }
        return f->f.directory() || Stream.of(matchers)
            .anyMatch(m -> m.matches(Paths.get("", f.path())));
    }

    public static Predicate<Found> only(String pattern) {
        if (pattern==null || pattern.isEmpty()) {
            return ALL;
        }
        PathMatcher matcher = FileSystems.getDefault().getPathMatcher(pattern);
        return f->f.directory() || matcher.matches(Paths.get("", f.path()));
    }

    @Override
    public Iterator<Found> iterator() {
        return this;
    }

    public void close() {
        closed = true; // signals decodeThread to stop
    }
}
