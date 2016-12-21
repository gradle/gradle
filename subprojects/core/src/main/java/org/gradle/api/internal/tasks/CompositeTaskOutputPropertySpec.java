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

import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.util.GFileUtils;

import java.io.File;
import java.util.Iterator;
import java.util.Map;

public class CompositeTaskOutputPropertySpec extends AbstractTaskOutputPropertySpec {

    private final CacheableTaskOutputFilePropertySpec.OutputType outputType;
    private final Object paths;
    private final String taskName;
    private final FileResolver resolver;

    public CompositeTaskOutputPropertySpec(TaskOutputs taskOutputs, String taskName, FileResolver resolver, CacheableTaskOutputFilePropertySpec.OutputType outputType, Object[] paths) {
        super(taskOutputs);
        this.taskName = taskName;
        this.resolver = resolver;
        this.outputType = outputType;
        this.paths = (paths != null && paths.length == 1)
            ? paths[0]
            : paths;
    }

    public CacheableTaskOutputFilePropertySpec.OutputType getOutputType() {
        return outputType;
    }

    public Iterator<TaskOutputFilePropertySpec> resolveToOutputProperties() {
        Object unpackedPaths = GFileUtils.unpack(paths);
        if (unpackedPaths == null) {
            return Iterators.emptyIterator();
        } else if (unpackedPaths instanceof Map) {
            final Iterator<? extends Map.Entry<?, ?>> iterator = ((Map<?, ?>) unpackedPaths).entrySet().iterator();
            return new AbstractIterator<TaskOutputFilePropertySpec>() {
                @Override
                protected TaskOutputFilePropertySpec computeNext() {
                    if (iterator.hasNext()) {
                        Map.Entry<?, ?> entry = iterator.next();
                        Object key = entry.getKey();
                        if (key == null) {
                            throw new IllegalArgumentException(String.format("Mapped output property '%s' has null key", getPropertyName()));
                        }
                        String id = key.toString();
                        File file = resolver.resolve(entry.getValue());
                        return new CacheableTaskOutputCompositeFilePropertyElementSpec(CompositeTaskOutputPropertySpec.this, "." + id, file);
                    }
                    return endOfData();
                }
            };
        } else {
            return Iterators.<TaskOutputFilePropertySpec>singletonIterator(
                new NonCacheableTaskOutputPropertySpec(taskOutputs, taskName, this, resolver, unpackedPaths)
            );
        }
    }
}
