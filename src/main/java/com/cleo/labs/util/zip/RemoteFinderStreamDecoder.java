package com.cleo.labs.util.zip;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.util.Iterator;
import java.util.NoSuchElementException;

import com.fasterxml.jackson.core.JsonParser.Feature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.ByteStreams;

public class RemoteFinderStreamDecoder implements Iterator<Found>, Iterable<Found>, AutoCloseable {

    private enum State {
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

    private InputStream in;
    private State state = State.GET;
    private Found next = null;
    private IOException exception = null;

    private static final ObjectMapper mapper = new ObjectMapper()
            .configure(Feature.AUTO_CLOSE_SOURCE, false);

    /**
     * Creates a new decoder whose elements are parsed from {@code in}.
     * <p/>
     * {@code in} is not closed when the iterator terminates.
     * @param in
     */
    public RemoteFinderStreamDecoder(InputStream in) {
        this.in = in;
    }

    /**
     * Gets the next element if the current element has been consumed
     * (as indicated by State.GET, which means "need to go get another one").
     * Returns {@code true} if the next element is available in {@code next},
     * with state set to State.GOT.
     * <p/>
     * Advances state to State.DONE if no more elements are found or if an
     * exception occurs.
     * @return
     */
    private boolean get() {
        if (state == State.GET) { 
            try {
                byte[] buf = new byte[Integer.BYTES];
                try {
                    ByteStreams.readFully(in, buf);
                } catch (EOFException e) {
                    next = null;
                    state = State.DONE;
                }
                if (state != State.DONE) {
                    int length = ByteBuffer.wrap(buf).getInt();
                    next = mapper.readValue(ByteStreams.limit(in, length), Found.class);
                    next.remote(true);
                    state = State.GOT;
                }
            } catch (IOException e) {
                exception = e;
                next = null;
                state = State.DONE;
            }
        }
        return state == State.GOT;
    }

    public IOException exception() {
        return exception;
    }

    public void throwIfException() throws IOException {
        if (exception != null) {
            throw exception;
        }
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

    @Override
    public Iterator<Found> iterator() {
        return this;
    }

    @Override
    public void close() {
        if (in!=null) {
            try {
                in.close();
            } catch (IOException e) {
                exception = e;
            }
        }
    }

}
