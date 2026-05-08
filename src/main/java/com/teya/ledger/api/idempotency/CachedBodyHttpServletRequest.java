package com.teya.ledger.api.idempotency;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/**
 * {@link HttpServletRequestWrapper} that reads the entire request body into
 * an in-memory byte array on construction and serves all subsequent
 * {@code getInputStream()} / {@code getReader()} calls from that buffer.
 *
 * <p>Spring's {@code ContentCachingRequestWrapper} cannot be used here:
 * it only accumulates bytes as the underlying stream is read, so a
 * pre-controller caller (e.g., the idempotency interceptor in
 * {@code preHandle}) sees an empty buffer because nothing has read the
 * body yet. This wrapper buffers eagerly so both the interceptor and
 * the controller see the full body.
 */
final class CachedBodyHttpServletRequest extends HttpServletRequestWrapper {

    private final byte[] body;

    CachedBodyHttpServletRequest(HttpServletRequest request) throws IOException {
        super(request);
        this.body = request.getInputStream().readAllBytes();
    }

    /** @return the buffered request body bytes (never null; possibly empty). */
    byte[] getBody() {
        return body;
    }

    @Override
    public ServletInputStream getInputStream() {
        return new BufferedServletInputStream(new ByteArrayInputStream(body));
    }

    @Override
    public BufferedReader getReader() {
        return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
    }

    /** Adapts a {@link ByteArrayInputStream} to the {@link ServletInputStream} contract. */
    private static final class BufferedServletInputStream extends ServletInputStream {

        private final ByteArrayInputStream delegate;

        BufferedServletInputStream(ByteArrayInputStream delegate) {
            this.delegate = delegate;
        }

        @Override
        public boolean isFinished() {
            return delegate.available() == 0;
        }

        @Override
        public boolean isReady() {
            return true;
        }

        @Override
        public void setReadListener(ReadListener readListener) {
            // Synchronous mode only; non-blocking I/O is not used.
        }

        @Override
        public int read() {
            return delegate.read();
        }

        @Override
        public int read(byte[] b, int off, int len) {
            return delegate.read(b, off, len);
        }
    }
}
