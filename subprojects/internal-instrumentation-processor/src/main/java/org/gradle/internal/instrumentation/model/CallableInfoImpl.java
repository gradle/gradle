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

import java.util.List;

public class CallableInfoImpl implements CallableInfo {
    private final CallableKindInfo kind;
    private final CallableOwnerInfo owner;
    private final String callableName;
    private final CallableReturnTypeInfo returnType;
    private final List<ParameterInfo> parameters;

    public CallableInfoImpl(CallableKindInfo kind, CallableOwnerInfo owner, String callableName, CallableReturnTypeInfo returnType, List<ParameterInfo> parameters) {
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
    public CallableOwnerInfo getOwner() {
        return owner;
    }

    @Override
    public String getCallableName() {
        return callableName;
    }

    @Override
    public CallableReturnTypeInfo getReturnType() {
        return returnType;
    }

    @Override
    public List<ParameterInfo> getParameters() {
        return parameters;
    }
}
