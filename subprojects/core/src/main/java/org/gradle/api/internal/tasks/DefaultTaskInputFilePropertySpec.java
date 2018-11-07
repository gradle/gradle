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
import org.gradle.api.internal.file.FileResolver;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.internal.fingerprint.AbsolutePathInputNormalizer;
import org.gradle.internal.fingerprint.IgnoredPathInputNormalizer;
import org.gradle.internal.fingerprint.NameOnlyInputNormalizer;
import org.gradle.internal.fingerprint.RelativePathInputNormalizer;

@NonNullApi
public class DefaultTaskInputFilePropertySpec implements DeclaredTaskInputFileProperty {

    private final ValidatingValue value;
    private final ValidationAction validationAction;
    private final TaskPropertyFileCollection files;
    private String propertyName;
    private boolean skipWhenEmpty;
    private boolean optional;
    private Class<? extends FileNormalizer> normalizer = AbsolutePathInputNormalizer.class;
    private LifecycleAwareTaskProperty lifecycleAware;

    public DefaultTaskInputFilePropertySpec(String taskDisplayName, FileResolver resolver, ValidatingValue value, ValidationAction validationAction) {
        this.value = value;
        this.validationAction = validationAction;
        this.files = new TaskPropertyFileCollection(taskDisplayName, "input", this, resolver, value);
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
        this.propertyName = TaskPropertyUtils.checkPropertyName(propertyName);
        return this;
    }

    @Override
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
    public TaskInputFilePropertyBuilderInternal withPathSensitivity(PathSensitivity sensitivity) {
        return withNormalizer(determineNormalizerForPathSensitivity(sensitivity));
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
    public String toString() {
        return getPropertyName() + " (" + normalizer.getSimpleName().replace("Normalizer", "") + ")";
    }

    @Override
    public int compareTo(TaskPropertySpec o) {
        return getPropertyName().compareTo(o.getPropertyName());
    }

    @Override
    public void prepareValue() {
        value.maybeFinalizeValue();
        Object obj = value.call();
        // TODO - move this to ValidatingValue instead
        if (obj instanceof LifecycleAwareTaskProperty) {
            lifecycleAware = (LifecycleAwareTaskProperty) obj;
            lifecycleAware.prepareValue();
        }
    }

    @Override
    public void cleanupValue() {
        if (lifecycleAware != null) {
            lifecycleAware.cleanupValue();
        }
    }

    private Class<? extends FileNormalizer> determineNormalizerForPathSensitivity(PathSensitivity pathSensitivity) {
        switch (pathSensitivity) {
            case NONE:
                return IgnoredPathInputNormalizer.class;
            case NAME_ONLY:
                return NameOnlyInputNormalizer.class;
            case RELATIVE:
                return RelativePathInputNormalizer.class;
            case ABSOLUTE:
                return AbsolutePathInputNormalizer.class;
            default:
                throw new IllegalArgumentException("Unknown path sensitivity: " + pathSensitivity);
        }
    }
}
