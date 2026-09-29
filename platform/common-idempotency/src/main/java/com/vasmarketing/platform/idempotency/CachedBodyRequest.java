package com.vasmarketing.platform.idempotency;

import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** A request whose body was read eagerly (bounded) so it can be hashed and then re-read. */
final class CachedBodyRequest extends HttpServletRequestWrapper {

  private final byte[] body;

  private CachedBodyRequest(HttpServletRequest request, byte[] body) {
    super(request);
    this.body = body;
  }

  static CachedBodyRequest wrap(HttpServletRequest request, int maxBytes) throws IOException {
    try (InputStream in = request.getInputStream()) {
      byte[] body = in.readNBytes(maxBytes + 1);
      if (body.length > maxBytes) {
        throw new BodyTooLargeException(maxBytes);
      }
      return new CachedBodyRequest(request, body);
    }
  }

  byte[] body() {
    return body.clone();
  }

  @Override
  public ServletInputStream getInputStream() {
    ByteArrayInputStream source = new ByteArrayInputStream(body);
    return new ServletInputStream() {
      @Override
      public boolean isFinished() {
        return source.available() == 0;
      }

      @Override
      public boolean isReady() {
        return true;
      }

      @Override
      public void setReadListener(ReadListener listener) {
        throw new UnsupportedOperationException("async reads are not supported");
      }

      @Override
      public int read() {
        return source.read();
      }
    };
  }

  @Override
  public BufferedReader getReader() {
    return new BufferedReader(new InputStreamReader(getInputStream(), StandardCharsets.UTF_8));
  }

  static final class BodyTooLargeException extends IOException {
    private static final long serialVersionUID = 1L;

    BodyTooLargeException(int maxBytes) {
      super("Idempotent request bodies are limited to " + maxBytes + " bytes.");
    }
  }
}
