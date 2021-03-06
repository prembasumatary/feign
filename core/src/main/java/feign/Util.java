/*
 * Copyright 2013 Netflix, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package feign;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.lang.reflect.Array;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.lang.reflect.WildcardType;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;

import static java.lang.String.format;

/**
 * Utilities, typically copied in from guava, so as to avoid dependency conflicts.
 */
public class Util {

  /**
   * The HTTP Content-Length header field name.
   */
  public static final String CONTENT_LENGTH = "Content-Length";
  /**
   * The HTTP Content-Encoding header field name.
   */
  public static final String CONTENT_ENCODING = "Content-Encoding";
  /**
   * The HTTP Retry-After header field name.
   */
  public static final String RETRY_AFTER = "Retry-After";
  /**
   * Value for the Content-Encoding header that indicates that GZIP encoding is in use.
   */
  public static final String ENCODING_GZIP = "gzip";
  /**
   * Value for the Content-Encoding header that indicates that DEFLATE encoding is in use.
   */
  public static final String ENCODING_DEFLATE = "deflate";
  /**
   * UTF-8: eight-bit UCS Transformation Format.
   */
  public static final Charset UTF_8 = Charset.forName("UTF-8");

  // com.google.common.base.Charsets
  /**
   * ISO-8859-1: ISO Latin Alphabet Number 1 (ISO-LATIN-1).
   */
  public static final Charset ISO_8859_1 = Charset.forName("ISO-8859-1");
  private static final int BUF_SIZE = 0x800; // 2K chars (4K bytes)

  private Util() { // no instances
  }

  /**
   * Copy of {@code com.google.common.base.Preconditions#checkArgument}.
   */
  public static void checkArgument(boolean expression,
                                   String errorMessageTemplate,
                                   Object... errorMessageArgs) {
    if (!expression) {
      throw new IllegalArgumentException(
          format(errorMessageTemplate, errorMessageArgs));
    }
  }

  /**
   * Copy of {@code com.google.common.base.Preconditions#checkNotNull}.
   */
  public static <T> T checkNotNull(T reference,
                                   String errorMessageTemplate,
                                   Object... errorMessageArgs) {
    if (reference == null) {
      // If either of these parameters is null, the right thing happens anyway
      throw new NullPointerException(
          format(errorMessageTemplate, errorMessageArgs));
    }
    return reference;
  }

  /**
   * Copy of {@code com.google.common.base.Preconditions#checkState}.
   */
  public static void checkState(boolean expression,
                                String errorMessageTemplate,
                                Object... errorMessageArgs) {
    if (!expression) {
      throw new IllegalStateException(
          format(errorMessageTemplate, errorMessageArgs));
    }
  }

  /**
   * Adapted from {@code com.google.common.base.Strings#emptyToNull}.
   */
  public static String emptyToNull(String string) {
    return string == null || string.isEmpty() ? null : string;
  }

  /**
   * Adapted from {@code com.google.common.base.Strings#emptyToNull}.
   */
  @SuppressWarnings("unchecked")
  public static <T> T[] toArray(Iterable<? extends T> iterable, Class<T> type) {
    Collection<T> collection;
    if (iterable instanceof Collection) {
      collection = (Collection<T>) iterable;
    } else {
      collection = new ArrayList<T>();
      for (T element : iterable) {
        collection.add(element);
      }
    }
    T[] array = (T[]) Array.newInstance(type, collection.size());
    return collection.toArray(array);
  }

  /**
   * Returns an unmodifiable collection which may be empty, but is never null.
   */
  public static <T> Collection<T> valuesOrEmpty(Map<String, Collection<T>> map, String key) {
    return map.containsKey(key) && map.get(key) != null ? map.get(key) : Collections.<T>emptyList();
  }

  public static void ensureClosed(Closeable closeable) {
    if (closeable != null) {
      try {
        closeable.close();
      } catch (IOException ignored) { // NOPMD
      }
    }
  }

  /**
   * Resolves the last type parameter of the parameterized {@code supertype}, based on the {@code
   * genericContext}, into its upper bounds. <p/> Implementation copied from {@code
   * retrofit.RestMethodInfo}.
   *
   * @param genericContext Ex. {@link java.lang.reflect.Field#getGenericType()}
   * @param supertype      Ex. {@code Decoder.class}
   * @return in the example above, the type parameter of {@code Decoder}.
   * @throws IllegalStateException if {@code supertype} cannot be resolved into a parameterized type
   *                               using {@code context}.
   */
  public static Type resolveLastTypeParameter(Type genericContext, Class<?> supertype)
      throws IllegalStateException {
    Type resolvedSuperType =
        Types.getSupertype(genericContext, Types.getRawType(genericContext), supertype);
    checkState(resolvedSuperType instanceof ParameterizedType,
               "could not resolve %s into a parameterized type %s",
               genericContext, supertype);
    Type[] types = ParameterizedType.class.cast(resolvedSuperType).getActualTypeArguments();
    for (int i = 0; i < types.length; i++) {
      Type type = types[i];
      if (type instanceof WildcardType) {
        types[i] = ((WildcardType) type).getUpperBounds()[0];
      }
    }
    return types[types.length - 1];
  }

  /**
   * Adapted from {@code com.google.common.io.CharStreams.toString()}.
   */
  public static String toString(Reader reader) throws IOException {
    if (reader == null) {
      return null;
    }
    try {
      StringBuilder to = new StringBuilder();
      CharBuffer buf = CharBuffer.allocate(BUF_SIZE);
      while (reader.read(buf) != -1) {
        buf.flip();
        to.append(buf);
        buf.clear();
      }
      return to.toString();
    } finally {
      ensureClosed(reader);
    }
  }

  /**
   * Adapted from {@code com.google.common.io.ByteStreams.toByteArray()}.
   */
  public static byte[] toByteArray(InputStream in) throws IOException {
    checkNotNull(in, "in");
    try {
      ByteArrayOutputStream out = new ByteArrayOutputStream();
      copy(in, out);
      return out.toByteArray();
    } finally {
      ensureClosed(in);
    }
  }

  /**
   * Adapted from {@code com.google.common.io.ByteStreams.copy()}.
   */
  private static long copy(InputStream from, OutputStream to)
      throws IOException {
    checkNotNull(from, "from");
    checkNotNull(to, "to");
    byte[] buf = new byte[BUF_SIZE];
    long total = 0;
    while (true) {
      int r = from.read(buf);
      if (r == -1) {
        break;
      }
      to.write(buf, 0, r);
      total += r;
    }
    return total;
  }

  static String decodeOrDefault(byte[] data, Charset charset, String defaultValue) {
    if (data == null) {
      return defaultValue;
    }
    checkNotNull(charset, "charset");
    try {
      return charset.newDecoder().decode(ByteBuffer.wrap(data)).toString();
    } catch (CharacterCodingException ex) {
      return defaultValue;
    }
  }
}
