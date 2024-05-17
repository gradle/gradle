/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.internal.exceptions;

import java.util.SortedSet;
import java.util.TreeSet;

public class ValueCollectingDiagnosticsVisitor implements DiagnosticsVisitor {
    private final TreeSet<String> values = new TreeSet<String>();

    public SortedSet<String> getValues() {
        return values;
    }

    @Override
    public DiagnosticsVisitor candidate(String displayName) {
        return this;
    }

    @Override
    public DiagnosticsVisitor example(String example) {
        return this;
    }

    @Override
    public DiagnosticsVisitor values(Iterable<?> values) {
        for (Object value : values) {
            this.values.add(value.toString());
        }
        return this;
    }
}
