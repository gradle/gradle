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

import org.gradle.internal.file.TreeType;

public enum  OutputFilePropertyType {
    FILE(TreeType.FILE, ValidationActions.OUTPUT_FILE_VALIDATOR),
    DIRECTORY(TreeType.DIRECTORY, ValidationActions.OUTPUT_DIRECTORY_VALIDATOR),
    FILES(TreeType.FILE, ValidationActions.OUTPUT_FILES_VALIDATOR),
    DIRECTORIES(TreeType.DIRECTORY, ValidationActions.OUTPUT_DIRECTORIES_VALIDATOR);

    private final TreeType outputType;
    private final ValidationAction validationAction;

    OutputFilePropertyType(TreeType outputType, ValidationAction validationAction) {
        this.outputType = outputType;
        this.validationAction = validationAction;
    }

    public TreeType getOutputType() {
        return outputType;
    }

    public ValidationAction getValidationAction() {
        return validationAction;
    }
}
