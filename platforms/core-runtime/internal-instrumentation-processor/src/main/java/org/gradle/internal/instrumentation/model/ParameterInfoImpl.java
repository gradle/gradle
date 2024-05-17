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

import org.objectweb.asm.Type;

public class ParameterInfoImpl implements ParameterInfo {
    private final String name;
    private final Type parameterType;
    private final ParameterKindInfo kind;

    public ParameterInfoImpl(String name, Type parameterType, ParameterKindInfo kind) {
        this.name = name;
        this.parameterType = parameterType;
        this.kind = kind;
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public Type getParameterType() {
        return parameterType;
    }

    @Override
    public ParameterKindInfo getKind() {
        return kind;
    }
}
