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

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.gradle.api.file.FileCollection;

import java.util.List;
import java.util.SortedMap;

public class FilePropertyContainer<T extends TaskFilePropertySpec> {
    protected final List<T> fileProperties = Lists.newArrayList();
    private final String displayName;
    private SortedMap<String, FileCollection> filePropertiesMap;

    public FilePropertyContainer(String displayName) {
        this.displayName = displayName;
    }

    // Note: sorted map used to keep order of properties consistent
    public SortedMap<String, FileCollection> getFileProperties() {
        if (filePropertiesMap == null) {
            int unnamedPropertyCounter = 0;
            SortedMap<String, FileCollection> filePropertiesMap = Maps.newTreeMap();
            for (T propertySpec : fileProperties) {
                String propertyName = propertySpec.getPropertyName();
                if (propertyName == null) {
                    propertyName = "$" + (++unnamedPropertyCounter);
                }
                if (filePropertiesMap.containsKey(propertyName)) {
                    throw new IllegalArgumentException(String.format("Multiple %s file properties with name '%s'", displayName, propertyName));
                }
                filePropertiesMap.put(propertyName, propertySpec.getPropertyFiles());
            }
            this.filePropertiesMap = filePropertiesMap;
        }
        return filePropertiesMap;
    }
}
