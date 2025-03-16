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

import org.gradle.internal.instrumentation.api.annotations.ParameterKind.CallerClassName;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind.InjectVisitorContext;
import org.gradle.internal.instrumentation.api.annotations.ParameterKind.KotlinDefaultMask;

import java.util.List;

public interface CallableInfo {
    CallableKindInfo getKind();

    CallableOwnerInfo getOwner();

    String getCallableName();

    CallableReturnTypeInfo getReturnType();

    List<ParameterInfo> getParameters();

    /**
     * Returns true if the interceptor method has a parameter annotated with {@link KotlinDefaultMask}.
     *
     * @return true if the method has a default mask parameter
     */
    default boolean hasKotlinDefaultMaskParam() {
        return getParameters().stream().anyMatch(it -> it.getKind() == ParameterKindInfo.KOTLIN_DEFAULT_MASK);
    }

    /**
     * Returns true if the interceptor method has a parameter annotated with {@link CallerClassName}.
     *
     * @return true if the method has a caller class name parameter
     */
    default boolean hasCallerClassNameParam() {
        return getParameters().stream().anyMatch(it -> it.getKind() == ParameterKindInfo.CALLER_CLASS_NAME);
    }

    /**
     * Returns true if the interceptor method has a parameter annotated with {@link InjectVisitorContext}.
     *
     * @return true if the method has a visitor context parameter
     */
    default boolean hasInjectVisitorContextParam() {
        return getParameters().stream().anyMatch(it -> it.getKind() == ParameterKindInfo.INJECT_VISITOR_CONTEXT);
    }
}
