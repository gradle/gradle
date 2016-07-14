/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks;

import com.google.common.base.Objects;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Maps;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskPropertyBuilder;

import java.util.Iterator;
import java.util.SortedMap;
import java.util.SortedSet;

public class FilePropertyContainer {
    private final String displayName;

    public FilePropertyContainer(String displayName) {
        this.displayName = displayName;
    }

    // Note: sorted map used to keep order of properties consistent
    protected SortedSet<TaskFilePropertySpec> collectFileProperties(Iterator<? extends TaskFilePropertySpec> fileProperties) {
        SortedMap<String, TaskFilePropertySpec> filePropertiesMap = Maps.newTreeMap();
        while (fileProperties.hasNext()) {
            TaskFilePropertySpec propertySpec = fileProperties.next();
            String propertyName = propertySpec.getPropertyName();
            if (filePropertiesMap.containsKey(propertyName)) {
                throw new IllegalArgumentException(String.format("Multiple %s file properties with name '%s'", displayName, propertyName));
            }
            filePropertiesMap.put(propertyName, new ImmutableFilePropertySpec(propertyName, propertySpec.getPropertyFiles()));
        }
        return ImmutableSortedSet.copyOf(filePropertiesMap.values());
    }

    protected static <T extends TaskPropertySpec & TaskPropertyBuilder> void ensurePropertiesHaveNames(Iterable<T> properties) {
        int unnamedPropertyCounter = 0;
        for (T propertySpec : properties) {
            String propertyName = propertySpec.getPropertyName();
            if (propertyName == null) {
                propertyName = "$" + (++unnamedPropertyCounter);
                propertySpec.withPropertyName(propertyName);
            }
        }
    }

    private static class ImmutableFilePropertySpec implements TaskFilePropertySpec {
        private final String propertyName;
        private final FileCollection propertyFiles;

        public ImmutableFilePropertySpec(String propertyName, FileCollection propertyFiles) {
            this.propertyName = propertyName;
            this.propertyFiles = propertyFiles;
        }

        @Override
        public String getPropertyName() {
            return propertyName;
        }

        @Override
        public FileCollection getPropertyFiles() {
            return propertyFiles;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ImmutableFilePropertySpec that = (ImmutableFilePropertySpec) o;
            return Objects.equal(propertyName, that.propertyName)
                && Objects.equal(propertyFiles, that.propertyFiles);
        }

        @Override
        public int hashCode() {
            return Objects.hashCode(propertyName, propertyFiles);
        }

        @Override
        public int compareTo(TaskPropertySpec o) {
            return propertyName.compareTo(o.getPropertyName());
        }
    }
}
