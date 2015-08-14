/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.internal.manage.schema;

import org.gradle.model.internal.core.NodeInitializer;
import org.gradle.model.internal.type.ModelType;

public interface ModelSchema<T> {
    NodeInitializer getNodeInitializer();

    ModelType<T> getType();

    Kind getKind();

    public enum Kind {
        VALUE(false, true), // at the moment we are conflating this with unstructured primitives
        COLLECTION,
        SPECIALIZED_MAP(false, false), // not quite
        STRUCT,
        UNMANAGED_STRUCT(false, false); // some type we know nothing about

        private final boolean isManaged;
        private final boolean isAllowedPropertyTypeOfManagedType;

        Kind() {
            this(true, true);
        }

        Kind(boolean isManaged, boolean isAllowedPropertyTypeOfManagedType) {
            this.isManaged = isManaged;
            this.isAllowedPropertyTypeOfManagedType = isAllowedPropertyTypeOfManagedType;
        }

        public boolean isManaged() {
            return isManaged;
        }

        public boolean isAllowedPropertyTypeOfManagedType() {
            return isAllowedPropertyTypeOfManagedType;
        }
    }
}
