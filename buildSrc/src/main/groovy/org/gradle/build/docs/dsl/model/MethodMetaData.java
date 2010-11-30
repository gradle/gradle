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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class MethodMetaData implements Serializable, LanguageElement {
    private final String name;
    private final ClassMetaData ownerClass;
    private final List<String> parameterTypes = new ArrayList<String>();
    private String rawCommentText;
    private String returnType;

    public MethodMetaData(String name, ClassMetaData ownerClass) {
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

    public ClassMetaData getOwnerClass() {
        return ownerClass;
    }

    public String getReturnType() {
        return returnType;
    }

    public void setReturnType(String returnType) {
        this.returnType = returnType;
    }

    public List<String> getParameterTypes() {
        return parameterTypes;
    }
    
    public String getRawCommentText() {
        return rawCommentText;
    }

    public void setRawCommentText(String rawCommentText) {
        this.rawCommentText = rawCommentText;
    }

    public String getSignature() {
        StringBuilder builder = new StringBuilder();
        builder.append(returnType);
        builder.append(' ');
        builder.append(name);
        builder.append('(');
        for (int i = 0; i < parameterTypes.size(); i++) {
            String paramType =  parameterTypes.get(i);
            if (i > 0) {
                builder.append(", ");
            }
            builder.append(paramType);
            builder.append(" param");
            builder.append(i+1);
        }
        builder.append(')');
        return builder.toString();
    }
}
