/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.tools.api.impl;

import org.jspecify.annotations.Nullable;

/**
 * A single entry of the {@code MethodParameters} attribute of a method.
 *
 * <p>Parameter names are part of the API, because annotation processors read them via
 * {@code VariableElement.getSimpleName()}. Unlike other members, parameters keep their
 * declaration order, since that order determines which parameter a name belongs to.</p>
 */
public class ParameterMember {

    @Nullable
    private final String name;
    private final int access;

    public ParameterMember(@Nullable String name, int access) {
        this.name = name;
        this.access = access;
    }

    /**
     * The parameter name, or {@code null} when the class file records no name for it.
     */
    @Nullable
    public String getName() {
        return name;
    }

    public int getAccess() {
        return access;
    }

    @Override
    public String toString() {
        return name == null ? "<unnamed>" : name;
    }
}
