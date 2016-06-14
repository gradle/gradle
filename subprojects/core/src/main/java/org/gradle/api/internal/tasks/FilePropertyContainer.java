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

import com.google.common.collect.Maps;
import org.gradle.api.file.FileCollection;
import org.gradle.api.tasks.TaskPropertyBuilder;

import java.util.Iterator;
import java.util.SortedMap;

public class FilePropertyContainer {
    private final String displayName;

    public FilePropertyContainer(String displayName) {
        this.displayName = displayName;
    }

    // Note: sorted map used to keep order of properties consistent
    protected SortedMap<String, FileCollection> collectFileProperties(Iterator<? extends TaskFilePropertySpec> fileProperties) {
        SortedMap<String, FileCollection> filePropertiesMap = Maps.newTreeMap();
        while (fileProperties.hasNext()) {
            TaskFilePropertySpec propertySpec = fileProperties.next();
            String propertyName = propertySpec.getPropertyName();
            if (filePropertiesMap.containsKey(propertyName)) {
                throw new IllegalArgumentException(String.format("Multiple %s file properties with name '%s'", displayName, propertyName));
            }
            filePropertiesMap.put(propertyName, propertySpec.getPropertyFiles());
        }
        return filePropertiesMap;
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
}
