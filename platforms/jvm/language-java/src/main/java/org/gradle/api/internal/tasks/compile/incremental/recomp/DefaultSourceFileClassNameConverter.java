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

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class DefaultSourceFileClassNameConverter implements SourceFileClassNameConverter {
    private final Map<String, Set<String>> sourceClassesMapping;
    private final Map<String, Set<String>> classSourceMapping;

    public DefaultSourceFileClassNameConverter(Map<String, Set<String>> sourceClassesMapping) {
        this.sourceClassesMapping = sourceClassesMapping;
        this.classSourceMapping = constructReverseMapping(sourceClassesMapping);
    }

    private Map<String, Set<String>> constructReverseMapping(Map<String, Set<String>> sourceClassesMapping) {
        Map<String, Set<String>> reverse = new HashMap<>();
        for (Map.Entry<String, ? extends Collection<String>> entry : sourceClassesMapping.entrySet()) {
            for (String cls : entry.getValue()) {
                reverse.computeIfAbsent(cls, key -> new HashSet<>()).add(entry.getKey());
            }
        }
        return reverse;
    }

    @Override
    public Set<String> getClassNames(String sourceFileRelativePath) {
        return sourceClassesMapping.getOrDefault(sourceFileRelativePath, Collections.emptySet());
    }

    public Set<String> getRelativeSourcePaths(String fqcn) {
        return classSourceMapping.getOrDefault(fqcn, Collections.emptySet());
    }

    @Override
    public Set<String> getRelativeSourcePathsThatExist(String className) {
        return getRelativeSourcePaths(className);
    }

}
