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
import com.squareup.javapoet.TypeName;
import org.objectweb.asm.Type;

/**
 * Used since we don't want to depend on Gradle types directly, since that way we have to depend on Gradle projects like core-api.
 * And we don't want to depend on Gradle projects since that way we can't apply this annotation processor to them.
 */
public enum GradleReferencedType {
    DEPRECATION_LOGGER("org.gradle.internal.deprecation.DeprecationLogger"),
    GENERATED_ANNOTATION("org.gradle.api.Generated"),
    LIST_PROPERTY_LIST_VIEW("org.gradle.api.internal.provider.views.ListPropertyListView"),
    SET_PROPERTY_SET_VIEW("org.gradle.api.internal.provider.views.SetPropertySetView"),
    MAP_PROPERTY_MAP_VIEW("org.gradle.api.internal.provider.views.MapPropertyMapView");

    @SuppressWarnings("ImmutableEnumChecker")
    private final Type type;

    GradleReferencedType(String name) {
        this.type = Type.getType("L" + name.replace('.', '/') + ";");
    }

    public TypeName asTypeName() {
        return asClassName();
    }

    public ClassName asClassName() {
        return TypeUtils.className(type);
    }
}
