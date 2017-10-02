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
import org.gradle.api.internal.changedetection.state.InputPathNormalizationStrategy;
import org.gradle.api.internal.changedetection.state.PathNormalizationStrategy;
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskInputs;

import static org.gradle.api.internal.changedetection.state.InputPathNormalizationStrategy.ABSOLUTE;
import static org.gradle.api.internal.tasks.TaskPropertyUtils.checkPropertyName;

@NonNullApi
public class DefaultTaskInputFilePropertySpec extends TaskInputsDeprecationSupport implements DeclaredTaskInputFileProperty {

    private final ValidatingValue value;
    private final ValidationAction validationAction;
    private final TaskPropertyFileCollection files;
    private String propertyName;
    private boolean skipWhenEmpty;
    private boolean optional;
    private PathNormalizationStrategy pathNormalizationStrategy = ABSOLUTE;
    private Class<? extends FileNormalizer> normalizer = GenericFileNormalizer.class;

    public DefaultTaskInputFilePropertySpec(String taskName, FileResolver resolver, ValidatingValue paths, ValidationAction validationAction) {
        this.value = paths;
        this.validationAction = validationAction;
        this.files = new TaskPropertyFileCollection(taskName, "input", this, resolver, paths);
    }

    @Override
    public String getPropertyName() {
        return propertyName;
    }

    @Override
    public FileCollection getPropertyFiles() {
        return files;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal withPropertyName(String propertyName) {
        this.propertyName = checkPropertyName(propertyName);
        return this;
    }

    public boolean isSkipWhenEmpty() {
        return skipWhenEmpty;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal skipWhenEmpty(boolean skipWhenEmpty) {
        this.skipWhenEmpty = skipWhenEmpty;
        return this;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal skipWhenEmpty() {
        return skipWhenEmpty(true);
    }

    public boolean isOptional() {
        return optional;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal optional(boolean optional) {
        this.optional = optional;
        return this;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal optional() {
        return optional(true);
    }

    @Override
    public PathNormalizationStrategy getPathNormalizationStrategy() {
        return pathNormalizationStrategy;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal withPathSensitivity(PathSensitivity sensitivity) {
        return withPathNormalizationStrategy(InputPathNormalizationStrategy.valueOf(sensitivity));
    }

    @Override
    public TaskInputFilePropertyBuilderInternal withPathNormalizationStrategy(PathNormalizationStrategy pathNormalizationStrategy) {
        this.pathNormalizationStrategy = pathNormalizationStrategy;
        return this;
    }

    @Override
    public TaskInputFilePropertyBuilderInternal withNormalizer(Class<? extends FileNormalizer> normalizer) {
        this.normalizer = normalizer;
        return this;
    }

    @Override
    public Class<? extends FileNormalizer> getNormalizer() {
        return normalizer;
    }

    @Override
    public void validate(TaskValidationContext context) {
        value.validate(getPropertyName(), optional, validationAction, context);
    }

    @Override
    protected TaskInputs getTaskInputs(String method) {
        throw new UnsupportedOperationException(String.format("Chaining of the TaskInputs.%s method is not supported since Gradle 4.0.", method));
    }

    @Override
    public String toString() {
        return getPropertyName() + " (" + pathNormalizationStrategy + ")";
    }

    @Override
    public int compareTo(TaskPropertySpec o) {
        return getPropertyName().compareTo(o.getPropertyName());
    }
}
