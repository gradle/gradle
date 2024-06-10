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

import com.squareup.javapoet.TypeName;
import org.objectweb.asm.Type;

public enum GradleLazyType {
    CONFIGURABLE_FILE_COLLECTION("org.gradle.api.file.ConfigurableFileCollection"),
    FILE_COLLECTION("org.gradle.api.file.FileCollection"),
    DIRECTORY_PROPERTY("org.gradle.api.file.DirectoryProperty"),
    REGULAR_FILE_PROPERTY("org.gradle.api.file.RegularFileProperty"),
    LIST_PROPERTY("org.gradle.api.provider.ListProperty"),
    SET_PROPERTY("org.gradle.api.provider.SetProperty"),
    MAP_PROPERTY("org.gradle.api.provider.MapProperty"),
    PROPERTY("org.gradle.api.provider.Property"),
    UNSUPPORTED((Type) null) {
        @Override
        public Type asType() {
            throw new UnsupportedOperationException("Unsupported type");
        }
    };

    @SuppressWarnings("ImmutableEnumChecker")
    private final Type type;

    GradleLazyType(String name) {
        this(Type.getType("L" + name.replace('.', '/') + ";"));
    }

    GradleLazyType(Type type) {
        this.type = type;
    }

    public Type asType() {
        return type;
    }

    public TypeName asTypeName() {
        return TypeUtils.typeName(type);
    }

    public static boolean isAnyOf(Type type) {
        for (GradleLazyType gradleType : values()) {
            if (gradleType.type != null && gradleType.type.equals(type)) {
                return true;
            }
        }
        return false;
    }

    public static GradleLazyType from(Type type) {
        for (GradleLazyType gradleType : values()) {
            if (gradleType.type != null && gradleType.type.equals(type)) {
                return gradleType;
            }
        }
        throw new UnsupportedOperationException("Unknown lazy type: " + type.getClassName());
    }

    public static GradleLazyType from(String name) {
        for (GradleLazyType gradleType : values()) {
            if (gradleType.type != null && gradleType.type.getClassName().equals(name)) {
                return gradleType;
            }
        }
        throw new UnsupportedOperationException("Unknown lazy type: " + name);
    }
}
