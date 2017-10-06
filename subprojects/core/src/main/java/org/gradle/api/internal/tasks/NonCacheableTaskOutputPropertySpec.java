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

import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.changedetection.state.PathNormalizationStrategy;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.FileNormalizer;

@NonNullApi
public class NonCacheableTaskOutputPropertySpec extends TaskOutputsDeprecationSupport implements TaskOutputFilePropertySpec {

    private final CompositeTaskOutputPropertySpec parent;
    private final int index;
    private final FileCollection files;

    public NonCacheableTaskOutputPropertySpec(String taskName, CompositeTaskOutputPropertySpec parent, int index, FileResolver resolver, Object paths) {
        this.parent = parent;
        this.index = index;
        this.files = new TaskPropertyFileCollection(taskName, "output", this, resolver, paths);
    }

    @Override
    public String getPropertyName() {
        return parent.getPropertyName() + "$" + index;
    }

    public String getOriginalPropertyName() {
        return parent.getPropertyName();
    }

    @Override
    public OutputType getOutputType() {
        return parent.getOutputType();
    }

    @Override
    public Class<? extends FileNormalizer> getNormalizer() {
        return parent.getNormalizer();
    }

    @Override
    public PathNormalizationStrategy getPathNormalizationStrategy() {
        return parent.getPathNormalizationStrategy();
    }

    @Override
    public int compareTo(TaskPropertySpec o) {
        return getPropertyName().compareTo(o.getPropertyName());
    }

    @Override
    public FileCollection getPropertyFiles() {
        return files;
    }
}
