/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.properties;

import com.google.common.base.Function;
import com.google.common.collect.ImmutableSortedSet;
import com.google.common.collect.Iterables;
import com.google.common.collect.Iterators;
import org.gradle.api.NonNullApi;
import org.gradle.api.internal.tasks.CacheableTaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.CompositeTaskOutputPropertySpec;
import org.gradle.api.internal.tasks.TaskOutputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskPropertySpec;
import org.gradle.api.internal.tasks.TaskPropertyUtils;
import org.gradle.internal.Factory;

import javax.annotation.Nullable;
import java.io.File;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

@NonNullApi
public class GetOutputFilesVisitor extends PropertyVisitor.Adapter {
    private List<TaskOutputFilePropertySpec> declaredOutputFileProperties = new ArrayList<TaskOutputFilePropertySpec>();
    private ImmutableSortedSet<TaskOutputFilePropertySpec> fileProperties;
    private boolean hasDeclaredOutputs;

    @Override
    public void visitOutputFileProperty(TaskOutputFilePropertySpec outputFileProperty) {
        hasDeclaredOutputs = true;
        declaredOutputFileProperties.add(outputFileProperty);
    }

    public Factory<ImmutableSortedSet<TaskOutputFilePropertySpec>> getFilePropertiesFactory() {
        return new Factory<ImmutableSortedSet<TaskOutputFilePropertySpec>>() {
            @Nullable
            @Override
            public ImmutableSortedSet<TaskOutputFilePropertySpec> create() {
                if (fileProperties == null) {
                    Iterator<TaskOutputFilePropertySpec> flattenedProperties = Iterators.concat(Iterables.transform(declaredOutputFileProperties, new Function<TaskPropertySpec, Iterator<? extends TaskOutputFilePropertySpec>>() {
                        @Override
                        public Iterator<? extends TaskOutputFilePropertySpec> apply(@Nullable TaskPropertySpec propertySpec) {
                            if (propertySpec instanceof CompositeTaskOutputPropertySpec) {
                                return ((CompositeTaskOutputPropertySpec) propertySpec).resolveToOutputProperties();
                            } else {
                                if (propertySpec instanceof CacheableTaskOutputFilePropertySpec) {
                                    File outputFile = ((CacheableTaskOutputFilePropertySpec) propertySpec).getOutputFile();
                                    if (outputFile == null) {
                                        return Iterators.emptyIterator();
                                    }
                                }
                                return Iterators.singletonIterator((TaskOutputFilePropertySpec) propertySpec);
                            }
                        }
                    }).iterator());
                    fileProperties = TaskPropertyUtils.collectFileProperties("output", flattenedProperties);
                }
                return fileProperties;
            }
        };
    }

    public boolean hasDeclaredOutputs() {
        return hasDeclaredOutputs;
    }
}
