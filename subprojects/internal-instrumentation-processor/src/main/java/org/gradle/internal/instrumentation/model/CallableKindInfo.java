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

package org.gradle.internal.instrumentation.model;

import org.gradle.internal.instrumentation.api.annotations.CallableKind;

import java.lang.annotation.Annotation;

public enum CallableKindInfo {
    STATIC_METHOD, INSTANCE_METHOD, AFTER_CONSTRUCTOR, GROOVY_PROPERTY;

    public static CallableKindInfo fromAnnotation(Annotation annotation) {
        if (annotation instanceof CallableKind.StaticMethod) {
            return STATIC_METHOD;
        }
        if (annotation instanceof CallableKind.InstanceMethod) {
            return INSTANCE_METHOD;
        }
        if (annotation instanceof CallableKind.AfterConstructor) {
            return AFTER_CONSTRUCTOR;
        }
        if (annotation instanceof CallableKind.GroovyProperty) {
            return GROOVY_PROPERTY;
        }
        throw new IllegalArgumentException("Unexpected annotation " + annotation);
    }
}
