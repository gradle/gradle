/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.artifacts.specs;

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.specs.Spec;

/**
 * Various {@link Spec} implementations for selecting {@link Dependency} instances.
 *
 * @author Hans Dockter
 */
public class DependencySpecs {
    public static Spec<Dependency> type(Type type) {
        return new DependencyTypeSpec<Dependency>(type);
    }

    private static class DependencyTypeSpec<T extends Dependency> implements Spec<T> {

        private Type type;

        public DependencyTypeSpec(Type type) {
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
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            DependencyTypeSpec typeSpec = (DependencyTypeSpec) o;

            if (type != typeSpec.type) {
                return false;
            }

            return true;
        }

        @Override
        public int hashCode() {
            return type != null ? type.hashCode() : 0;
        }
    }

}