/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.model.internal.reflect.problems;

import org.gradle.problems.ValidationProblemId;

public enum CoreValidationProblemId implements ValidationProblemId {
    VALUE_NOT_SET,
    ANNOTATION_INVALID_IN_CONTEXT,
    MISSING_ANNOTATION,
    INCOMPATIBLE_ANNOTATIONS,
    INVALID_USE_OF_TYPE_ANNOTATION,
    MISSING_NORMALIZATION_ANNOTATION,
    INCORRECT_USE_OF_INPUT_ANNOTATION,
    UNRESOLVABLE_INPUT,
    IMPLICIT_DEPENDENCY,
    INPUT_FILE_DOES_NOT_EXIST,
    UNEXPECTED_INPUT_FILE_TYPE,
    CANNOT_WRITE_OUTPUT,
    CANNOT_WRITE_TO_RESERVED_LOCATION,
    UNSUPPORTED_NOTATION,
    CANNOT_USE_OPTIONAL_ON_PRIMITIVE_TYPE,
    NOT_CACHEABLE_WITHOUT_REASON;

    @Override
    public String getId() {
        return name();
    }
}
