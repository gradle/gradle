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
package org.gradle.build.docs.dsl.source.model;

import org.gradle.api.Action;

import java.io.Serializable;

/**
 * Static meta-data about a property extracted from the source for the class.
 */
public class PropertyMetaData extends AbstractLanguageElement implements Serializable, TypeContainer {
    private TypeMetaData type;
    private final String name;
    private final ClassMetaData ownerClass;
    private MethodMetaData setter;
    private MethodMetaData getter;

    public PropertyMetaData(String name, ClassMetaData ownerClass) {
        this.name = name;
        this.ownerClass = ownerClass;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return String.format("%s.%s", ownerClass, name);
    }

    public TypeMetaData getType() {
        return type;
    }

    public void setType(TypeMetaData type) {
        this.type = type;
    }

    public boolean isWriteable() {
        return setter != null;
    }

    public boolean isReadable() {
        return getter != null;
    }

    public boolean isProviderApi() {
        // TODO: Crude approximation
        return setter == null && (getType().getName().contains("Provider") || getType().getName().contains("Property"));
    }

    public ClassMetaData getOwnerClass() {
        return ownerClass;
    }

    public String getSignature() {
        StringBuilder builder = new StringBuilder();
        builder.append(type.getSignature());
        builder.append(' ');
        builder.append(name);
        return builder.toString();
    }

    public MethodMetaData getGetter() {
        return getter;
    }

    public void setGetter(MethodMetaData getter) {
        this.getter = getter;
    }

    public MethodMetaData getSetter() {
        return setter;
    }

    public void setSetter(MethodMetaData setter) {
        this.setter = setter;
    }

    public PropertyMetaData getOverriddenProperty() {
        MethodMetaData overriddenMethod = null;
        if (getter != null) {
            overriddenMethod = getter.getOverriddenMethod();
        }
        if (overriddenMethod == null && setter != null) {
            overriddenMethod = setter.getOverriddenMethod();
        }
        if (overriddenMethod != null) {
            return overriddenMethod.getOwnerClass().findDeclaredProperty(name);
        }

        return null;
    }

    @Override
    public void visitTypes(Action<TypeMetaData> action) {
        action.execute(type);
    }
}
