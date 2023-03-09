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

import org.gradle.internal.instrumentation.api.annotations.ParameterKind;

import java.lang.annotation.Annotation;

public enum ParameterKindInfo {
    RECEIVER, METHOD_PARAMETER, VARARG_METHOD_PARAMETER, CALLER_CLASS_NAME, KOTLIN_DEFAULT_MASK;

    public boolean isSourceParameter() {
        return this == METHOD_PARAMETER || this == VARARG_METHOD_PARAMETER;
    }

    public static ParameterKindInfo fromAnnotation(Annotation annotation) {
        if (annotation instanceof ParameterKind.Receiver) {
            return RECEIVER;
        }
        if (annotation instanceof ParameterKind.CallerClassName) {
            return CALLER_CLASS_NAME;
        }
        if (annotation instanceof ParameterKind.KotlinDefaultMask) {
            return KOTLIN_DEFAULT_MASK;
        }
        if (annotation instanceof ParameterKind.VarargParameter) {
            return VARARG_METHOD_PARAMETER;
        }
        throw new IllegalArgumentException("Unexpected annotation " + annotation);
    }
}
