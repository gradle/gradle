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
package org.gradle.api.internal.tasks.properties.annotations;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.tasks.properties.InputFilePropertyType;
import org.gradle.api.tasks.InputFiles;
import org.gradle.internal.reflect.AnnotationCategory;

import java.lang.annotation.Annotation;

import static org.gradle.api.internal.tasks.properties.ModifierAnnotationCategory.INCREMENTAL;
import static org.gradle.api.internal.tasks.properties.ModifierAnnotationCategory.NORMALIZATION;
import static org.gradle.api.internal.tasks.properties.ModifierAnnotationCategory.OPTIONAL;

public class InputFilesPropertyAnnotationHandler extends AbstractInputFilePropertyAnnotationHandler {
    @Override
    public Class<? extends Annotation> getAnnotationType() {
        return InputFiles.class;
    }

    @Override
    public ImmutableSet<? extends AnnotationCategory> getAllowedModifiers() {
        return ImmutableSet.of(INCREMENTAL, NORMALIZATION, OPTIONAL);
    }

    @Override
    protected InputFilePropertyType getFilePropertyType() {
        return InputFilePropertyType.FILES;
    }
}
