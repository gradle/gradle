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

package org.gradle.api.internal.tasks;

import org.gradle.api.NonNullApi;
import org.gradle.api.file.FileCollection;
import org.gradle.caching.internal.OutputType;

import javax.annotation.Nullable;
import java.io.File;

@NonNullApi
public class ResolvedTaskOutputFilePropertySpec extends AbstractTaskOutputPropertySpec implements CacheableTaskOutputFilePropertySpec {

    private final OutputType outputType;
    private final File outputRoot;
    private final String propertyName;

    public ResolvedTaskOutputFilePropertySpec(String propertyName, OutputType outputType, @Nullable File outputRoot) {
        this.propertyName = propertyName;
        this.outputType = outputType;
        this.outputRoot = outputRoot;
    }

    @Override
    public String getPropertyName() {
        return propertyName;
    }

    @Nullable
    @Override
    public File getOutputRoot() {
        return outputRoot;
    }

    @Override
    public OutputType getOutputType() {
        return outputType;
    }

    @Override
    public FileCollection getPropertyFiles() {
        throw new UnsupportedOperationException("not implemented");
    }
}
