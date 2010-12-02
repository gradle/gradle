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

public class TypeMetaData implements Serializable {
    public static final TypeMetaData VOID = new TypeMetaData("void");

    private String name;
    private int arrayDimensions;
    private boolean varargs;

    public TypeMetaData(String name) {
        this.name = name;
    }

    public TypeMetaData() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getArrayDimensions() {
        return arrayDimensions + (varargs ? 1 : 0);
    }

    public void addArrayDimension() {
        arrayDimensions++;
    }

    public boolean isVarargs() {
        return varargs;
    }

    public void setVarargs(boolean varargs) {
        this.varargs = varargs;
    }

    public String getSignature() {
        StringBuilder builder = new StringBuilder();
        builder.append(name);
        for (int i = 0; i < arrayDimensions; i++) {
            builder.append("[]");
        }
        if (varargs) {
            builder.append("...");
        }
        return builder.toString();
    }
}
