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
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.internal.tasks.properties.PropertyVisitor;
import org.gradle.api.internal.tasks.properties.PropertyWalker;
import org.gradle.internal.Cast;

import java.util.Iterator;
import java.util.Set;
import java.util.SortedSet;

@NonNullApi
public class TaskPropertyUtils {

    public static void visitProperties(PropertyWalker propertyWalker, final TaskInternal task, PropertyVisitor visitor) {
        final PropertySpecFactory specFactory = new DefaultPropertySpecFactory(task, ((ProjectInternal) task.getProject()).getFileResolver());
        propertyWalker.visitProperties(specFactory, visitor, task);
        task.getInputs().visitRegisteredProperties(visitor);
        task.getOutputs().visitRegisteredProperties(visitor);
        int destroyableCount = 0;
        for (Object path : ((TaskDestroyablesInternal) task.getDestroyables()).getRegisteredPaths()) {
            visitor.visitDestroyableProperty(new DefaultTaskDestroyablePropertySpec("$" + ++destroyableCount, path));
        }
        int localStateCount = 0;
        for (Object path : ((TaskLocalStateInternal) task.getLocalState()).getRegisteredPaths()) {
            visitor.visitLocalStateProperty(new DefaultTaskLocalStatePropertySpec("$" + ++localStateCount, path));
        }
    }

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

    public static <T extends TaskFilePropertySpec> SortedSet<ResolvedTaskOutputFilePropertySpec> resolveFileProperties(ImmutableSortedSet<T> properties) {
        ImmutableSortedSet.Builder<ResolvedTaskOutputFilePropertySpec> builder = ImmutableSortedSet.naturalOrder();
        for (T property : properties) {
            CacheableTaskOutputFilePropertySpec cacheableProperty = Cast.uncheckedCast(property);
            builder.add(new ResolvedTaskOutputFilePropertySpec(cacheableProperty.getPropertyName(), cacheableProperty.getOutputType(), cacheableProperty.getOutputFile()));
        }
        return builder.build();
    }

    public static String checkPropertyName(String propertyName) {
        if (propertyName.isEmpty()) {
            throw new IllegalArgumentException("Property name must not be empty string");
        }
        return propertyName;
    }
}
