/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.dependencies.filter;

import org.gradle.api.dependencies.Dependency;
import org.gradle.api.filter.FilterSpec;

/**
 * @author Hans Dockter
 */
public class TypeSpec<T extends Dependency> implements FilterSpec<T> {

    private Type type;

    public TypeSpec(Type type) {
        this.type = type;
    }

    public boolean isSatisfiedBy(Dependency dependency) {
        return type.isOf(dependency);
    }

    public Type getType() {
        return type;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;

        TypeSpec typeSpec = (TypeSpec) o;

        if (type != typeSpec.type) return false;

        return true;
    }

    @Override
    public int hashCode() {
        return type != null ? type.hashCode() : 0;
    }
}
