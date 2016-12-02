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

import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Sets;
import org.gradle.api.tasks.TaskPropertyBuilder;

import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;

public class TaskPropertyUtils {

    // Note: sorted set used to keep order of properties consistent
    public static <T extends TaskFilePropertySpec> SortedSet<T> collectFileProperties(String displayName, Iterator<? extends T> fileProperties) {
        Set<String> names = Sets.newHashSet();
        ImmutableSortedSet.Builder<T> builder = ImmutableSortedSet.naturalOrder();
        while (fileProperties.hasNext()) {
            T propertySpec = fileProperties.next();
            String propertyName = propertySpec.getPropertyName();
            if (!names.add(propertyName)) {
                throw new IllegalArgumentException(String.format("Multiple %s file properties with name '%s'", displayName, propertyName));
            }
            builder.add(propertySpec);
        }
        return builder.build();
    }

    public static <T extends TaskPropertySpec & TaskPropertyBuilder> void ensurePropertiesHaveNames(Iterable<T> properties) {
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
