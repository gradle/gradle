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

package org.gradle.api.internal.tasks.properties;

import com.google.common.collect.AbstractIterator;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.TaskOutputs;

import java.io.File;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.Callable;

import static org.gradle.util.GUtil.uncheckedCall;

public class CompositeTaskOutputPropertySpec extends AbstractTaskOutputPropertySpec implements Iterable<CacheableTaskOutputFilePropertySpec> {

    private final CacheableTaskOutputFilePropertySpec.OutputType outputType;
    private final Callable<Map<?, ?>> paths;
    private final FileResolver resolver;

    public CompositeTaskOutputPropertySpec(TaskOutputs taskOutputs, FileResolver resolver, CacheableTaskOutputFilePropertySpec.OutputType outputType, Callable<Map<?, ?>> paths) {
        super(taskOutputs);
        this.resolver = resolver;
        this.outputType = outputType;
        this.paths = paths;
    }

    public CacheableTaskOutputFilePropertySpec.OutputType getOutputType() {
        return outputType;
    }

    @Override
    public Iterator<CacheableTaskOutputFilePropertySpec> iterator() {
        final Iterator<? extends Map.Entry<?, ?>> iterator = uncheckedCall(paths).entrySet().iterator();
        return new AbstractIterator<CacheableTaskOutputFilePropertySpec>() {
            @Override
            protected CacheableTaskOutputFilePropertySpec computeNext() {
                if (iterator.hasNext()) {
                    Map.Entry<?, ?> entry = iterator.next();
                    String id = entry.getKey().toString();
                    File file = resolver.resolve(entry.getValue());
                    return new DefaultTaskOutputFilePropertySpec(CompositeTaskOutputPropertySpec.this, "." + id, file);
                }
                return endOfData();
            }
        };
    }
}
