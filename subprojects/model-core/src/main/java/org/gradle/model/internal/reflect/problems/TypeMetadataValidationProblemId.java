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

public enum TypeMetadataValidationProblemId implements ValidationProblemId {
    IGNORED_ANNOTATIONS_ON_FIELD,
    IGNORED_ANNOTATIONS_ON_METHOD,
    MUTABLE_TYPE_WITH_SETTER,
    REDUNDANT_GETTERS,
    PRIVATE_GETTER_MUST_NOT_BE_ANNOTATED,
    IGNORED_PROPERTY_MUST_NOT_BE_ANNOTATED,
    CONFLICTING_ANNOTATIONS;

    @Override
    public String getId() {
        return name();
    }
}
