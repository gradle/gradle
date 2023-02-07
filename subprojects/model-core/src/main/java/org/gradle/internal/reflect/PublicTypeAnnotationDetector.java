/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.reflect;

import org.gradle.api.reflect.PublicType;
import org.gradle.api.reflect.TypeOf;

import javax.annotation.Nullable;

public class PublicTypeAnnotationDetector {

    private PublicTypeAnnotationDetector() {
        // utility class, blocking the creation of instances
    }

    @Nullable
    public static TypeOf<?> detect(Object instance) {
        return detect(instance.getClass());
    }

    @Nullable
    public static TypeOf<?> detect(@Nullable Class<?> type) {
        while (type != null) {
            if (type.isAnnotationPresent(PublicType.class)) {
                return TypeOf.typeOf(type.getAnnotation(PublicType.class).value());
            }
            type = type.getSuperclass();
        }
        return null;
    }

}
