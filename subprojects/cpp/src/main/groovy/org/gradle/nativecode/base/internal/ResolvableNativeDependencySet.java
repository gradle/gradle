/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativecode.base.internal;

import org.gradle.api.internal.notations.api.NotationParser;
import org.gradle.nativecode.base.NativeDependencySet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class ResolvableNativeDependencySet {
    private final List<Object> libs = new ArrayList<Object>();

    public void add(Object lib) {
        this.libs.add(lib);
    }

    public Collection<NativeDependencySet> resolve() {
        NotationParser<NativeDependencySet> parser = NativeDependencyNotationParser.parser();
        List<NativeDependencySet> result = new ArrayList<NativeDependencySet>();
        for (Object lib : libs) {
            result.add(parser.parseNotation(lib));
        }
        return result;
    }
}
