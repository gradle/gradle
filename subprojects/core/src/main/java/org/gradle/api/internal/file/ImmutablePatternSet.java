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

package org.gradle.api.internal.file;

import groovy.lang.Closure;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.PatternSet;

// TODO: this doesn't quite guarantee immutability, because the source may be holding closures that are doing god knows what
public class ImmutablePatternSet extends PatternSet {

    public static ImmutablePatternSet of(PatternSet source) {
        if (source instanceof ImmutablePatternSet) {
            return (ImmutablePatternSet) source;
        } else {
            return new ImmutablePatternSet(source);
        }
    }

    private ImmutablePatternSet(PatternSet source) {
        super(source);
        copyFrom(source);
    }

    @Override
    public PatternSet setIncludes(Iterable<String> includes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PatternSet include(String... includes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PatternSet include(Iterable includes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PatternSet include(Spec<FileTreeElement> spec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void setCaseSensitive(boolean caseSensitive) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PatternSet setExcludes(Iterable<String> excludes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PatternSet includeSpecs(Iterable<Spec<FileTreeElement>> includeSpecs) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PatternSet include(Closure closure) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PatternSet exclude(String... excludes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PatternSet exclude(Iterable excludes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PatternSet excludeSpecs(Iterable<Spec<FileTreeElement>> excludes) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PatternSet exclude(Spec<FileTreeElement> spec) {
        throw new UnsupportedOperationException();
    }

    @Override
    public PatternSet exclude(Closure closure) {
        throw new UnsupportedOperationException();
    }
}
