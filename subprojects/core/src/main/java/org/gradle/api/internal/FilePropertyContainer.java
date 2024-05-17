/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal;

import org.gradle.api.internal.tasks.TaskPropertyRegistration;
import org.gradle.api.tasks.TaskFilePropertyBuilder;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * Container for {@link TaskPropertyRegistration}s that might not have a name. The container
 * ensures that whenever parameters are iterated they are always assigned a name.
 */
public class FilePropertyContainer<T extends TaskFilePropertyBuilder & TaskPropertyRegistration> implements Iterable<T> {
    private final List<T> properties = new ArrayList<>();
    private boolean changed;
    private int unnamedPropertyCounter;

    private FilePropertyContainer() {
    }

    public static <T extends TaskFilePropertyBuilder & TaskPropertyRegistration> FilePropertyContainer<T> create() {
        return new FilePropertyContainer<T>();
    }

    public void add(T property) {
        properties.add(property);
        changed = true;
    }

    @Override
    public Iterator<T> iterator() {
        if (changed) {
            for (T propertySpec : properties) {
                String propertyName = propertySpec.getPropertyName();
                if (propertyName == null) {
                    propertyName = "$" + ++unnamedPropertyCounter;
                    propertySpec.withPropertyName(propertyName);
                }
            }
            changed = false;
        }
        return properties.iterator();
    }
}
