/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.instrumentation.processor.codegen;

import com.squareup.javapoet.ClassName;
import com.squareup.javapoet.ParameterizedTypeName;
import com.squareup.javapoet.TypeName;

public enum GradleLazyType {
    CONFIGURABLE_FILE_COLLECTION("org.gradle.api.file.ConfigurableFileCollection"),
    CONFIGURABLE_FILE_TREE("org.gradle.api.file.ConfigurableFileTree"),
    FILE_COLLECTION("org.gradle.api.file.FileCollection"),
    FILE_TREE("org.gradle.api.file.FileTree"),
    DIRECTORY_PROPERTY("org.gradle.api.file.DirectoryProperty"),
    REGULAR_FILE_PROPERTY("org.gradle.api.file.RegularFileProperty"),
    LIST_PROPERTY("org.gradle.api.provider.ListProperty"),
    SET_PROPERTY("org.gradle.api.provider.SetProperty"),
    MAP_PROPERTY("org.gradle.api.provider.MapProperty"),
    PROPERTY("org.gradle.api.provider.Property"),
    PROVIDER("org.gradle.api.provider.Provider"),
    UNSUPPORTED((ClassName) null) {
        @Override
        public ClassName asClassName() {
            throw new UnsupportedOperationException("Unsupported type");
        }
    };

    @SuppressWarnings("ImmutableEnumChecker")
    private final ClassName className;

    GradleLazyType(String name) {
        this(ClassName.bestGuess(name));
    }

    GradleLazyType(ClassName className) {
        this.className = className;
    }

    public ClassName asClassName() {
        return className;
    }

    public boolean isEqualToRawTypeOf(TypeName typeName) {
        if (typeName instanceof ParameterizedTypeName) {
            typeName = ((ParameterizedTypeName) typeName).rawType;
        }
        return className.equals(typeName);
    }

    public static GradleLazyType from(TypeName typeName) {
        String binaryName;
        if (typeName instanceof ClassName) {
            binaryName = ((ClassName) typeName).reflectionName();
        } else if (typeName instanceof ParameterizedTypeName) {
            binaryName = ((ParameterizedTypeName) typeName).rawType.reflectionName();
        } else {
            throw new UnsupportedOperationException("Cannot get binary name from TypeName: " + typeName.getClass());
        }
        return from(binaryName);
    }

    public static GradleLazyType from(String name) {
        for (GradleLazyType gradleType : values()) {
            if (gradleType.className != null && gradleType.className.reflectionName().equals(name)) {
                return gradleType;
            }
        }
        throw new UnsupportedOperationException("Unknown Gradle lazy type: " + name);
    }
}
