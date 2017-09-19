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
import org.gradle.api.NonNullApi;
import org.gradle.api.tasks.TaskFilePropertyBuilder;
import org.gradle.internal.Cast;

import javax.annotation.Nullable;
import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;

@NonNullApi
public class TaskPropertyUtils {

    // Note: sorted set used to keep order of properties consistent
    public static <T extends TaskFilePropertySpec> ImmutableSortedSet<T> collectFileProperties(String displayName, Iterator<? extends T> fileProperties) {
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

    public static <T extends TaskPropertySpec & TaskFilePropertyBuilder> void ensurePropertiesHaveNames(Iterable<T> properties) {
        int unnamedPropertyCounter = 0;
        for (T propertySpec : properties) {
            String propertyName = propertySpec.getPropertyName();
            if (propertyName == null) {
                propertyName = "$" + (++unnamedPropertyCounter);
                propertySpec.withPropertyName(propertyName);
            }
        }
    }

    public static <T extends TaskFilePropertySpec> SortedSet<ResolvedTaskOutputFilePropertySpec> resolveFileProperties(ImmutableSortedSet<T> properties) {
        ImmutableSortedSet.Builder<ResolvedTaskOutputFilePropertySpec> builder = ImmutableSortedSet.naturalOrder();
        for (T property : properties) {
            CacheableTaskOutputFilePropertySpec cacheableProperty = Cast.uncheckedCast(property);
            builder.add(new ResolvedTaskOutputFilePropertySpec(cacheableProperty.getPropertyName(), cacheableProperty.getOutputType(), cacheableProperty.getOutputFile()));
        }
        return builder.build();
    }

    @Nullable
    public static String checkPropertyName(@Nullable String propertyName) {
        if (propertyName != null) {
            if (propertyName.length() == 0) {
                throw new IllegalArgumentException("Property name must not be empty string");
            }
            if (!Character.isJavaIdentifierStart(propertyName.codePointAt(0))) {
                throw new IllegalArgumentException(String.format("Property name '%s' must be a valid Java identifier", propertyName));
            }
            boolean previousCharWasADot = false;
            for (int idx = 1; idx < propertyName.length(); idx++) {
                int chr = propertyName.codePointAt(idx);
                // Allow single dots except as the last element of the name
                if (chr == '.' && !previousCharWasADot && idx < propertyName.length() - 1) {
                    previousCharWasADot = true;
                    continue;
                }
                if (!Character.isJavaIdentifierPart(chr)) {
                    throw new IllegalArgumentException(String.format("Property name '%s' must be a valid Java identifier", propertyName));
                }
                previousCharWasADot = false;
            }
        }
        return propertyName;
    }
}
