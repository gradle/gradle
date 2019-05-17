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
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Objects;

/**
 * Static meta-data about a method extracted from the source for the class.
 */
public class MethodMetaData extends AbstractLanguageElement implements Serializable, TypeContainer {
    private final String name;
    private final ClassMetaData ownerClass;
    private final List<ParameterMetaData> parameters = new ArrayList<ParameterMetaData>();
    private TypeMetaData returnType;

    public MethodMetaData(String name, ClassMetaData ownerClass) {
        this.name = name;
        this.ownerClass = ownerClass;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return String.format("%s.%s()", ownerClass, name);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        MethodMetaData that = (MethodMetaData) o;
        return Objects.equals(name, that.name) &&
            Objects.equals(ownerClass.getClassName(), that.ownerClass.getClassName()) &&
            Objects.equals(parameters, that.parameters) &&
            Objects.equals(returnType, that.returnType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), name, ownerClass, parameters, returnType);
    }

    public ClassMetaData getOwnerClass() {
        return ownerClass;
    }

    public TypeMetaData getReturnType() {
        return returnType;
    }

    public void setReturnType(TypeMetaData returnType) {
        this.returnType = returnType;
    }

    public MethodMetaData getOverriddenMethod() {
        LinkedList<ClassMetaData> queue = new LinkedList<ClassMetaData>();
        queue.add(ownerClass.getSuperClass());
        queue.addAll(ownerClass.getInterfaces());

        String overrideSignature = getOverrideSignature();

        while (!queue.isEmpty()) {
            ClassMetaData cl = queue.removeFirst();
            if (cl == null) {
                continue;
            }
            MethodMetaData overriddenMethod = cl.findDeclaredMethod(overrideSignature);
            if (overriddenMethod != null) {
                return overriddenMethod;
            }
            queue.add(cl.getSuperClass());
            queue.addAll(cl.getInterfaces());
        }

        return null;
    }

    public List<ParameterMetaData> getParameters() {
        return parameters;
    }

    public ParameterMetaData addParameter(String name, TypeMetaData type) {
        ParameterMetaData param = new ParameterMetaData(name);
        param.setType(type);
        parameters.add(param);
        return param;
    }

    public String getSignature() {
        StringBuilder builder = new StringBuilder();
        builder.append(returnType.getSignature());
        builder.append(' ');
        builder.append(name);
        builder.append('(');
        for (int i = 0; i < parameters.size(); i++) {
            ParameterMetaData param = parameters.get(i);
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(param.getSignature());
        }
        builder.append(')');
        return builder.toString();
    }

    /**
     * Returns the signature of this method, excluding the return type, and converting generic types to their raw types.
     */
    public String getOverrideSignature() {
        StringBuilder builder = new StringBuilder();
        builder.append(name);
        builder.append('(');
        for (int i = 0; i < parameters.size(); i++) {
            ParameterMetaData param = parameters.get(i);
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(param.getType().getRawType().getSignature());
        }
        builder.append(')');
        return builder.toString();
    }

    @Override
    public void visitTypes(Action<TypeMetaData> action) {
        action.execute(returnType);
        for (ParameterMetaData parameter : parameters) {
            parameter.visitTypes(action);
        }
    }
}
