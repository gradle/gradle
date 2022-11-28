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
package org.gradle.internal.execution.model.annotations;

import org.gradle.api.tasks.InputDirectory;
import org.gradle.internal.fingerprint.DirectorySensitivity;
import org.gradle.internal.properties.InputFilePropertyType;
import org.gradle.internal.properties.annotations.PropertyMetadata;

import static org.gradle.internal.execution.model.annotations.ModifierAnnotationCategory.IGNORE_EMPTY_DIRECTORIES;
import static org.gradle.internal.execution.model.annotations.ModifierAnnotationCategory.INCREMENTAL;
import static org.gradle.internal.execution.model.annotations.ModifierAnnotationCategory.NORMALIZATION;
import static org.gradle.internal.execution.model.annotations.ModifierAnnotationCategory.NORMALIZE_LINE_ENDINGS;
import static org.gradle.internal.execution.model.annotations.ModifierAnnotationCategory.OPTIONAL;

public class InputDirectoryPropertyAnnotationHandler extends AbstractInputFilePropertyAnnotationHandler {
    public InputDirectoryPropertyAnnotationHandler() {
        super(
            InputDirectory.class,
            InputFilePropertyType.DIRECTORY,
            ModifierAnnotationCategory.annotationsOf(INCREMENTAL, NORMALIZATION, OPTIONAL, IGNORE_EMPTY_DIRECTORIES, NORMALIZE_LINE_ENDINGS)
        );
    }

    @Override
    protected DirectorySensitivity determineDirectorySensitivity(PropertyMetadata propertyMetadata) {
        // Being an input directory implies ignoring of empty directories.
        return DirectorySensitivity.IGNORE_DIRECTORIES;
    }
}
