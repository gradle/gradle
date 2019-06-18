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

import com.google.common.collect.Multimap;

import java.io.File;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;

public class GroovySourceFileClassNameConverter implements SourceFileClassNameConverter {
    private final Multimap<File, String> sourceClassesMapping;

    public GroovySourceFileClassNameConverter(Multimap<File, String> sourceClassesMapping) {
        this.sourceClassesMapping = sourceClassesMapping;
    }

    @Override
    public Collection<String> getClassNames(File groovySourceFile) {
        return sourceClassesMapping.get(groovySourceFile);
    }

    @Override
    public Optional<File> getFile(String fqcn) {
        for (Map.Entry<File, String> entry : sourceClassesMapping.entries()) {
            if (entry.getValue().equals(fqcn)) {
                return Optional.of(entry.getKey());
            }
        }
        return Optional.empty();
    }
}
