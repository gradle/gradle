/*
 * Copyright 2019 the original author or authors.
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

import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.LifecycleAwareTaskProperty;
import org.gradle.api.internal.tasks.PropertyFileCollection;
import org.gradle.api.internal.tasks.TaskInputFilePropertySpec;
import org.gradle.api.internal.tasks.TaskPropertySpec;
import org.gradle.api.internal.tasks.TaskValidationContext;
import org.gradle.api.internal.tasks.ValidatingTaskPropertySpec;
import org.gradle.api.internal.tasks.ValidatingValue;
import org.gradle.api.internal.tasks.ValidationAction;
import org.gradle.api.tasks.FileNormalizer;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.internal.file.PathToFileResolver;
import org.gradle.internal.fingerprint.AbsolutePathInputNormalizer;
import org.gradle.internal.fingerprint.IgnoredPathInputNormalizer;
import org.gradle.internal.fingerprint.NameOnlyInputNormalizer;
import org.gradle.internal.fingerprint.RelativePathInputNormalizer;

import java.util.function.Supplier;

public abstract class AbstractInputFilePropertySpec implements TaskInputFilePropertySpec, ValidatingTaskPropertySpec {

    private final ValidatingValue value;
    private final ValidationAction validationAction;
    private final PropertyFileCollection files;
    private LifecycleAwareTaskProperty lifecycleAware;

    public AbstractInputFilePropertySpec(
        String ownerDisplayName,
        PathToFileResolver resolver,
        ValidatingValue value,
        ValidationAction validationAction) {
        this.value = value;
        this.validationAction = validationAction;
        this.files = new PropertyFileCollection(ownerDisplayName, new Supplier<String>() {
            @Override
            public String get() {
                return getPropertyName();
            }
        }, "input", resolver, value);
    }

    @Override
    public FileCollection getPropertyFiles() {
        return files;
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
    public ValidatingValue getValue() {
        return value;
    }

    @Override
    public void cleanupValue() {
        if (lifecycleAware != null) {
            lifecycleAware.cleanupValue();
        }
    }

    @Override
    public void validate(TaskValidationContext context) {
        value.validate(getPropertyName(), isOptional(), validationAction, context);
    }

    @Override
    public String toString() {
        return getPropertyName() + " (" + getNormalizer().getSimpleName().replace("Normalizer", "") + ")";
    }

    @Override
    public int compareTo(TaskPropertySpec o) {
        return getPropertyName().compareTo(o.getPropertyName());
    }

    public static Class<? extends FileNormalizer> determineNormalizerForPathSensitivity(PathSensitivity pathSensitivity) {
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
