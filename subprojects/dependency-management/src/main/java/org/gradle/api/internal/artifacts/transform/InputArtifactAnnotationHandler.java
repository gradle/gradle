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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.artifacts.transform.InputArtifact;
import org.gradle.internal.execution.model.annotations.AbstractInputFilePropertyAnnotationHandler;
import org.gradle.internal.execution.model.annotations.ModifierAnnotationCategory;
import org.gradle.internal.instantiation.InjectAnnotationHandler;
import org.gradle.internal.properties.InputFilePropertyType;

import static org.gradle.internal.execution.model.annotations.ModifierAnnotationCategory.IGNORE_EMPTY_DIRECTORIES;
import static org.gradle.internal.execution.model.annotations.ModifierAnnotationCategory.INCREMENTAL;
import static org.gradle.internal.execution.model.annotations.ModifierAnnotationCategory.NORMALIZATION;
import static org.gradle.internal.execution.model.annotations.ModifierAnnotationCategory.NORMALIZE_LINE_ENDINGS;

public class InputArtifactAnnotationHandler extends AbstractInputFilePropertyAnnotationHandler implements InjectAnnotationHandler {
    public InputArtifactAnnotationHandler() {
        super(
            InputArtifact.class,
            InputFilePropertyType.FILE,
            ModifierAnnotationCategory.annotationsOf(INCREMENTAL, NORMALIZATION, IGNORE_EMPTY_DIRECTORIES, NORMALIZE_LINE_ENDINGS));
    }
}
