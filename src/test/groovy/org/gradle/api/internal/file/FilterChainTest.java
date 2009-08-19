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

import java.io.Reader;
import java.io.IOException;
import java.io.StringReader;
import org.junit.Test;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

public class FilterChainTest {

    @Test public void testSetInput() throws IOException {
        String test1 = "Testing1";
        String test2 = "something else";
        FilterChain chain = new FilterChain();
        chain.setInputSource(new StringReader(test1));
        assertThat(readToString(chain), equalTo(test1));

        chain.setInputSource(new StringReader(test2));
        assertThat(readToString(chain), equalTo(test2));        
    }

    @Test public void testMultipleChains() {
        FilterChain chain1 = new FilterChain();
        FilterChain chain2 = new FilterChain();
        FilterChain chain3 = new FilterChain();

        chain2.setInputSource(chain1);
        chain3.setInputSource(chain2);

        assertThat(chain3.findFirstFilterChain(), equalTo(chain1));
    }


    @Test public void testSingleChain() {
        FilterChain chain1 = new FilterChain();

        assertThat(chain1.findFirstFilterChain(), equalTo(chain1));
    }

    @Test public void testHasFilterSingle() {
        FilterChain chain1 = new FilterChain();
        assertFalse(chain1.hasFilters());

        chain1.addFilter(new StringReader(""));
        assertTrue(chain1.hasFilters());
    }

    @Test public void testHasFilterMultiple() {
        FilterChain chain1 = new FilterChain();
        FilterChain chain2 = new FilterChain();
        chain2.setInputSource(chain1);

        assertFalse(chain2.hasFilters());

        chain1.addFilter(new StringReader(""));
        assertTrue(chain2.hasFilters());
    }

    private String readToString(Reader filter) throws IOException {
        StringBuilder result = new StringBuilder();
        int nextChar = 0;
        while (nextChar != -1){
            nextChar = filter.read();
            if (nextChar != -1) {
                result.append((char)nextChar);
            }
        }
        return result.toString();
    }
}
