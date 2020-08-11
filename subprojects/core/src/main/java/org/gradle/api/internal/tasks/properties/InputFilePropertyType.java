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

public enum InputFilePropertyType {
    FILE(ValidationActions.INPUT_FILE_VALIDATOR),
    DIRECTORY(ValidationActions.INPUT_DIRECTORY_VALIDATOR),
    FILES(ValidationActions.NO_OP);

    private final ValidationAction validationAction;

    InputFilePropertyType(ValidationAction validationAction) {
        this.validationAction = validationAction;
    }

    public ValidationAction getValidationAction() {
        return validationAction;
    }
}
