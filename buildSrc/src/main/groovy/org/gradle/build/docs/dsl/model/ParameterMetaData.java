/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.build.docs.dsl.model;

import org.gradle.api.Action;

import java.io.Serializable;

public class ParameterMetaData implements Serializable, TypeContainer {
    private final MethodMetaData ownerMethod;
    private final String name;
    private TypeMetaData type;

    public ParameterMetaData(String name, MethodMetaData ownerMethod) {
        this.name = name;
        this.ownerMethod = ownerMethod;
    }

    public String getName() {
        return name;
    }

    public TypeMetaData getType() {
        return type;
    }

    public void setType(TypeMetaData type) {
        this.type = type;
    }

    public String getSignature() {
        StringBuilder builder = new StringBuilder();
        builder.append(type.getSignature());
        builder.append(" ");
        builder.append(name);
        return builder.toString();
    }

    public void visitTypes(Action<TypeMetaData> action) {
        action.execute(type);
    }
}
