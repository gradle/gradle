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

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.TaskOutputs;
import org.gradle.util.GFileUtils;

import java.io.File;

public class DefaultCacheableTaskOutputFilePropertySpec extends AbstractTaskOutputPropertySpec implements CacheableTaskOutputFilePropertySpec {
    private final TaskPropertyFileCollection files;
    private final OutputType outputType;
    private final FileResolver resolver;
    private final Object path;

    public DefaultCacheableTaskOutputFilePropertySpec(TaskOutputs taskOutputs, String taskName, FileResolver resolver, OutputType outputType, Object path) {
        super(taskOutputs);
        this.resolver = resolver;
        this.outputType = outputType;
        this.path = path;
        this.files = new TaskPropertyFileCollection(taskName, "output", this, resolver, path);
    }

    @Override
    public FileCollection getPropertyFiles() {
        return files;
    }

    @Override
    public File getOutputFile() {
        Object unpackedOutput = GFileUtils.unpack(path);
        if (unpackedOutput == null && isOptional()) {
            return null;
        }
        return resolver.resolve(path);
    }

    @Override
    public OutputType getOutputType() {
        return outputType;
    }
}
