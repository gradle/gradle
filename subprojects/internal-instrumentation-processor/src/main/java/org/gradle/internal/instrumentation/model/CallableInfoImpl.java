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

import java.util.List;

public class CallableInfoImpl implements CallableInfo {
    private final CallableKindInfo kind;
    private final Type owner;
    private final String callableName;
    private final Type returnType;
    private final List<ParameterInfo> parameters;

    public CallableInfoImpl(CallableKindInfo kind, Type owner, String callableName, Type returnType, List<ParameterInfo> parameters) {
        this.kind = kind;
        this.owner = owner;
        this.callableName = callableName;
        this.returnType = returnType;
        this.parameters = parameters;
    }

    @Override
    public CallableKindInfo getKind() {
        return kind;
    }

    @Override
    public Type getOwner() {
        return owner;
    }

    @Override
    public String getCallableName() {
        return callableName;
    }

    @Override
    public Type getReturnType() {
        return returnType;
    }

    @Override
    public List<ParameterInfo> getParameters() {
        return parameters;
    }
}
