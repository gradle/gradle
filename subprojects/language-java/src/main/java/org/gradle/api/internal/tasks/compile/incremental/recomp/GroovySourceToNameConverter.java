/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.recomp;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Multimap;

import java.io.File;
import java.util.Collection;

import static java.lang.String.format;

public class GroovySourceToNameConverter implements SourceToNameConverter {

    private CompilationSourceDirs sourceDirs;
    private final Multimap<File, String> sourceClassesMapping;

    public GroovySourceToNameConverter(CompilationSourceDirs sourceDirs, Multimap<File, String> sourceClassesMapping) {
        this.sourceDirs = sourceDirs;
        this.sourceClassesMapping = sourceClassesMapping;
    }

    @Override
    public Collection<String> getClassNames(File javaSourceClass) {
        Collection<String> classNames = sourceClassesMapping.get(javaSourceClass);
        if (!classNames.isEmpty()) {
            return ImmutableSet.copyOf(classNames);
        }

        throw new IllegalArgumentException(format("Unable to find source groovy class: '%s' because it does not belong to any of the source dirs: '%s'",
            javaSourceClass, sourceDirs.getSourceRoots()));

    }
}
