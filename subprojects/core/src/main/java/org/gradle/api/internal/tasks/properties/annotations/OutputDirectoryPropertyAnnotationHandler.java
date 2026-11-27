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

import org.gradle.api.tasks.OutputDirectory;
import org.gradle.internal.properties.OutputFilePropertyType;
import org.gradle.internal.properties.annotations.PropertyMetadata;
import org.gradle.internal.reflect.validation.TypeValidationContext;

import static org.gradle.internal.execution.model.annotations.FileAnnotationPropertyTypeValidation.LocationKind.FILE_COLLECTION;
import static org.gradle.internal.execution.model.annotations.FileAnnotationPropertyTypeValidation.LocationKind.REGULAR_FILE;
import static org.gradle.internal.execution.model.annotations.FileAnnotationPropertyTypeValidation.validateNotOfKind;

public class OutputDirectoryPropertyAnnotationHandler extends AbstractOutputPropertyAnnotationHandler {
    public OutputDirectoryPropertyAnnotationHandler() {
        super(OutputDirectory.class, OutputFilePropertyType.DIRECTORY);
    }

    @Override
    public void validatePropertyMetadata(PropertyMetadata propertyMetadata, TypeValidationContext validationContext) {
        validateNotOfKind(getAnnotationType(), "a directory", REGULAR_FILE, propertyMetadata, validationContext,
            "Annotate with @OutputFile for regular files",
            "Change the property type to DirectoryProperty for directories"
        );
        validateNotOfKind(getAnnotationType(), "a directory", FILE_COLLECTION, propertyMetadata, validationContext,
            "Annotate with @OutputDirectories for collections of directories",
            "Change the property type to DirectoryProperty for a single directory"
        );
    }
}
