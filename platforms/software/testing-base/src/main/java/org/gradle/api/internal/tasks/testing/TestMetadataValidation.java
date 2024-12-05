/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.internal.tasks.testing;

import org.gradle.model.internal.manage.schema.extract.ScalarTypes;
import org.gradle.model.internal.type.ModelType;

import javax.annotation.Nullable;
import java.io.Serializable;
import java.util.Collection;
import java.util.Map;

public class TestMetadataValidation {
    public static void checkAllowableTypes(Map<String, Object> metadata) {
        if (metadata.entrySet().stream().anyMatch(entry -> entry.getKey() == null)) {
            throw new IllegalArgumentException("Metadata key cannot be null");
        }
        metadata.entrySet().stream().filter(entry -> !isAllowedType(entry.getValue())).findFirst().ifPresent(entry -> {
            if (entry.getValue() == null) {
                throw new IllegalArgumentException(String.format("Metadata '%s' has null value", entry.getKey()));
            } else {
                throw new IllegalArgumentException(String.format("Metadata '%s' has unsupported value type '%s'", entry.getKey(), entry.getValue().getClass().getName()));
            }
        });
    }

    private static boolean isAllowedType(@Nullable Object value) {
        if (value == null) {
            return false;
        }

        if (ScalarTypes.isScalarType(ModelType.of(value.getClass()))) {
            return true;
        }

        if (value instanceof Collection && value instanceof Serializable) {
            return ((Collection<?>) value).stream().allMatch(TestMetadataValidation::isAllowedType);
        }

        return false;
    }
}
