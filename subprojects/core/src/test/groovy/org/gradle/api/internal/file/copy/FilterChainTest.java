/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.file.copy;

import com.google.common.io.ByteStreams;
import org.apache.commons.io.IOUtils;
import org.gradle.api.provider.Property;
import org.gradle.util.TestUtil;
import org.gradle.util.internal.WrapUtil;
import org.junit.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringReader;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.Collections;

import static org.gradle.util.internal.WrapUtil.toMap;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.instanceOf;
import static org.hamcrest.CoreMatchers.sameInstance;
import static org.hamcrest.MatcherAssert.assertThat;

public class FilterChainTest {
    private final FilterChain filterChain = new FilterChain();
    private final Reader originalReader = new StringReader("string");

    @Test
    public void usesOriginalReaderByDefault() {
        assertThat(filterChain.transform(originalReader), sameInstance(originalReader));
    }

    @Test
    public void canAddFilterReaderToEndOfChain() {
        filterChain.add(TestFilterReader.class);
        Reader transformedReader = filterChain.transform(originalReader);
        assertThat(transformedReader, instanceOf(TestFilterReader.class));
        TestFilterReader reader = (TestFilterReader) transformedReader;
        assertThat(reader.getIn(), sameInstance(originalReader));
    }

    @Test
    public void canAddFilterReaderWithParametersToEndOfChain() {
        filterChain.add(TestFilterReader.class, toMap("property", "value"));
        Reader transformedReader = filterChain.transform(originalReader);
        assertThat(transformedReader, instanceOf(TestFilterReader.class));
        TestFilterReader reader = (TestFilterReader) transformedReader;
        assertThat(reader.getIn(), sameInstance(originalReader));
        assertThat(reader.property, equalTo("value"));
    }

    @Test
    public void canAddLineFilterReaderToEndOfChain() {
        filterChain.add(TestUtil.TEST_CLOSURE);
        Reader transformedReader = filterChain.transform(originalReader);
        assertThat(transformedReader, instanceOf(LineFilter.class));
    }

    @Test
    public void canAddExpandFilterToEndOfChain() throws IOException {
        filterChain.expand(WrapUtil.toMap("prop", 1), escapeBackslashProperty());
        Reader transformedReader = filterChain.transform(new StringReader("[$prop][${prop+1}][<%= prop+2 %>]"));
        assertThat(IOUtils.toString(transformedReader), equalTo("[1][2][3]"));
    }

    @Test
    public void canFilterUsingISO88591() throws IOException {
        canFilterUsingCharset("ISO_8859_1");
    }

    @Test
    public void canFilterUsingUTF8() throws IOException {
        canFilterUsingCharset("UTF8");
    }

    private void canFilterUsingCharset(String charset) throws IOException {
        FilterChain filterChainWithCharset = new FilterChain(charset);
        filterChainWithCharset.expand(WrapUtil.toMap("prop", 1), escapeBackslashProperty());
        byte[] source = "éàüî $prop".getBytes(charset);

        InputStream transformedInputStream = filterChainWithCharset.transform(new ByteArrayInputStream(source));
        String actualResult = new String(ByteStreams.toByteArray(transformedInputStream), charset);

        String expectedResult = "éàüî 1";
        assertThat(actualResult, equalTo(expectedResult));
    }

    @Test
    public void canFilterSurrogatePairs() throws IOException {
        Charset charset = StandardCharsets.UTF_8;
        FilterChain filterChain = new FilterChain(charset.name());
        // This is a no-op transform, however it ensures FilterChain executes full byte->string->byte sequence
        filterChain.add(s -> s);

        // Make the input string longer to ensure the downstream implementations would have to use a buffer
        // instead of converting the full string at once
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < 10000; i++) {
            sb.append("丈, 😃, and नि");
        }
        String input = sb.toString();
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        InputStream transformed = filterChain.transform(new ByteArrayInputStream(input.getBytes(charset.name())));
        // We read byte-by-byte to trigger edge cases in the downstream implementations.
        int read;
        while ((read = transformed.read()) != -1) {
            baos.write(read);
        }
        assertThat(baos.toString(charset.name()), equalTo(input));
    }

    @Test
    public void convertsBackslashesIfNotEscaped() throws IOException {
        filterChain.expand(Collections.emptyMap(), escapeBackslashProperty());
        Reader transformedReader = filterChain.transform(new StringReader("\\n\\t\\\\"));
        assertThat(IOUtils.toString(transformedReader), equalTo("\n\t\\"));
    }

    @Test
    public void doNotConvertsBackslashesIfEscaped() throws IOException {
        filterChain.expand(Collections.emptyMap(), escapeBackslashProperty().value(true));
        Reader transformedReader = filterChain.transform(new StringReader("\\n\\t\\\\"));
        assertThat(IOUtils.toString(transformedReader), equalTo("\\n\\t\\\\"));
    }

    public static class TestFilterReader extends FilterReader {
        String property;

        public TestFilterReader(Reader reader) {
            super(reader);
        }

        public Reader getIn() {
            return in;
        }

        public void setProperty(String property) {
            this.property = property;
        }
    }

    private static Property<Boolean> escapeBackslashProperty() {
        return TestUtil.objectFactory().property(Boolean.class).value(false);
    }
}
