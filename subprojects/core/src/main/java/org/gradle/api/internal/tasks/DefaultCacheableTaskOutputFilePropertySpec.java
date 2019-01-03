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
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.file.TreeType;
import org.gradle.util.DeferredUtil;

import java.io.File;

@NonNullApi
public class DefaultCacheableTaskOutputFilePropertySpec extends AbstractTaskOutputPropertySpec implements CacheableTaskOutputFilePropertySpec, DeclaredTaskOutputFileProperty {
    private final TaskPropertyFileCollection files;
    private final TreeType outputType;
    private final PathToFileResolver resolver;
    private final ValidatingValue value;
    private final ValidationAction validationAction;

    public DefaultCacheableTaskOutputFilePropertySpec(String taskDisplayName, PathToFileResolver resolver, TreeType outputType, ValidatingValue value, ValidationAction validationAction) {
        this.resolver = resolver;
        this.outputType = outputType;
        this.value = value;
        this.validationAction = validationAction;
        this.files = new TaskPropertyFileCollection(taskDisplayName, "output", this, resolver, value);
    }

    @Override
    public FileCollection getPropertyFiles() {
        return files;
    }

    @Override
    public File getOutputFile() {
        Object unpackedOutput = DeferredUtil.unpack(value.call());
        if (unpackedOutput == null) {
            return null;
        }
        return resolver.resolve(unpackedOutput);
    }

    @Override
    public TreeType getOutputType() {
        return outputType;
    }

    @Override
    public void attachProducer(Task producer) {
        value.attachProducer(producer);
    }

    @Override
    public void prepareValue() {
        value.maybeFinalizeValue();
    }

    @Override
    public void cleanupValue() {
    }

    @Override
    public void validate(TaskValidationContext context) {
        value.validate(getPropertyName(), isOptional(), validationAction, context);
    }
}
