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

import java.io.FilterReader;
import java.io.Reader;
import java.io.StringReader;

public class FilterChain extends FilterReader {
    private FilterReader tail;

    protected FilterChain() {
        super(new StringReader(""));
        tail = this;
    }

    public void setHead(Reader in) {
        this.in = in;
    }

    public void setChain(FilterReader chain) {
        this.tail = chain;
    }

    public FilterReader getChain() {
        return tail;
    }

    public boolean hasChain() {
        return tail != this;
    }
}
