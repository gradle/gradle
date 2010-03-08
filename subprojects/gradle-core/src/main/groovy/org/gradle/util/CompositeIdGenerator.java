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

package org.gradle.util;

import java.io.Serializable;

public class CompositeIdGenerator implements IdGenerator<Object> {
    private final Object scope;
    private final IdGenerator<?> generator;

    public CompositeIdGenerator(Object scope, IdGenerator<?> generator) {
        this.scope = scope;
        this.generator = generator;
    }

    public Object generateId() {
        return new CompositeId(scope, generator.generateId());
    }
    
    private static class CompositeId implements Serializable {
        private final Object scope;
        private final Object id;

        private CompositeId(Object scope, Object id) {
            this.id = id;
            this.scope = scope;
        }

        @Override
        public boolean equals(Object o) {
            if (o == this) {
                return true;
            }
            if (o == null || o.getClass() != getClass()) {
                return false;
            }

            CompositeId other = (CompositeId) o;
            return other.id.equals(id) && other.scope.equals(scope);
        }

        @Override
        public int hashCode() {
            return scope.hashCode() ^ id.hashCode();
        }

        @Override
        public String toString() {
            return String.format("%s.%s", scope, id);
        }
    }
}
