/*
 * Copyright 2020 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.changedetection.state;

import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;
import java.nio.charset.CoderResult;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;

/**
 * A Java 8 compliant Charset implementation for reading properties files.  It first tries
 * to load the properties file using UTF-8 encoding and if it encounters an error, tries to
 * load the file using ISO-8859-1 encoding.
 *
 * This class is only necessary because {@code sun.util.PropertyResourceBundleCharset} is not
 * available in Java 8.  This class can be removed in favor of {@code sun.util.PropertyResourceBundleCharset}
 * once Java 8 is no longer supported.
 */
public class PropertyResourceBundleFallbackCharset extends Charset {

    public PropertyResourceBundleFallbackCharset() {
        super(PropertyResourceBundleFallbackCharset.class.getCanonicalName(), new String[0]);
    }

    @Override
    public boolean contains(Charset charset) {
        return false;
    }

    @Override
    public CharsetDecoder newDecoder() {
        return new FallbackDecoder(this);
    }

    @Override
    public CharsetEncoder newEncoder() {
        throw new UnsupportedOperationException();
    }

    private static final class FallbackDecoder extends CharsetDecoder {

        private CharsetDecoder decoder = StandardCharsets.UTF_8.newDecoder()
            .onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT);
        private boolean utf8 = true;

        protected FallbackDecoder(Charset charset) {
            super(charset, 1.0f, 1.0f);
        }

        static void mark(Buffer buffer) {
            buffer.mark();
        }

        static void reset(Buffer buffer) {
            buffer.reset();
        }

        protected CoderResult decodeLoop(ByteBuffer in, CharBuffer out) {
            mark(in);
            mark(out);

            CoderResult coderResult = decoder.decode(in, out, false);

            if (coderResult.isError() && utf8) {
                reset(in);
                reset(out);
                // Fallback to the ISO_8859_1 decoder
                decoder = StandardCharsets.ISO_8859_1.newDecoder();
                utf8 = false;
                return decoder.decode(in, out, false);
            } else {
                return coderResult;
            }
        }
    }
}
