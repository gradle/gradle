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
package org.gradle.api.internal.file;

import org.gradle.util.HelperUtil;
import org.junit.Test;

import java.io.FilterReader;
import java.io.Reader;
import java.io.StringReader;

import static org.gradle.util.WrapUtil.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

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
        filterChain.add(HelperUtil.TEST_CLOSURE);
        Reader transformedReader = filterChain.transform(originalReader);
        assertThat(transformedReader, instanceOf(LineFilter.class));
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
}
