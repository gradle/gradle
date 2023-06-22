/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.internal.reflect.problems;

public enum ValidationProblemId {
    // A problem id used only for test purposes
    TEST_PROBLEM,

    // Now every time you add a new validation problem, you need
    // to assign it an id, so that it can be referenced in tests
    INVALID_USE_OF_TYPE_ANNOTATION,
    MISSING_NORMALIZATION_ANNOTATION,
    VALUE_NOT_SET,
    CACHEABLE_TRANSFORM_CANT_USE_ABSOLUTE_SENSITIVITY,
    ARTIFACT_TRANSFORM_SHOULD_NOT_DECLARE_OUTPUT,
    IGNORED_ANNOTATIONS_ON_FIELD,
    IGNORED_ANNOTATIONS_ON_METHOD,
    MUTABLE_TYPE_WITH_SETTER,
    REDUNDANT_GETTERS,
    PRIVATE_GETTER_MUST_NOT_BE_ANNOTATED,
    IGNORED_PROPERTY_MUST_NOT_BE_ANNOTATED,
    CONFLICTING_ANNOTATIONS,
    ANNOTATION_INVALID_IN_CONTEXT,
    MISSING_ANNOTATION,
    INCOMPATIBLE_ANNOTATIONS,
    INCORRECT_USE_OF_INPUT_ANNOTATION,
    IMPLICIT_DEPENDENCY,
    INPUT_FILE_DOES_NOT_EXIST,
    UNEXPECTED_INPUT_FILE_TYPE,
    CANNOT_WRITE_OUTPUT,
    CANNOT_WRITE_TO_RESERVED_LOCATION,
    UNSUPPORTED_NOTATION,
    CANNOT_USE_OPTIONAL_ON_PRIMITIVE_TYPE,
    UNKNOWN_IMPLEMENTATION,
    NOT_CACHEABLE_WITHOUT_REASON,
    UNSUPPORTED_VALUE_TYPE,
    SERVICE_REFERENCE_MUST_BE_A_BUILD_SERVICE,
    NESTED_MAP_UNSUPPORTED_KEY_TYPE,
    NESTED_TYPE_UNSUPPORTED;
    public boolean onlyAffectsCacheableWork() {
        return this == MISSING_NORMALIZATION_ANNOTATION;
    }

    public static boolean onlyAffectsCacheableWork(String type) {
        return MISSING_NORMALIZATION_ANNOTATION.name().equals(type);
    }

}
