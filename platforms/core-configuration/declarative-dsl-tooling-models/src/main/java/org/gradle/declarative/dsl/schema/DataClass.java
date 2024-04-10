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

package org.gradle.declarative.dsl.schema;

import java.io.Serializable;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public interface DataClass extends Serializable {

    FqName getName();

    Set<FqName> getSupertypes();

    List<DataProperty> getProperties();

    List<SchemaMemberFunction> getMemberFunctions();

    List<DataConstructor> getConstructors();

    DataClass EMPTY = new DataClass() {
        @Override
        public FqName getName() {
            return FqName.EMPTY;
        }

        @Override
        public Set<FqName> getSupertypes() {
            return Collections.emptySet();
        }

        @Override
        public List<DataProperty> getProperties() {
            return Collections.emptyList();
        }

        @Override
        public List<SchemaMemberFunction> getMemberFunctions() {
            return Collections.emptyList();
        }

        @Override
        public List<DataConstructor> getConstructors() {
            return Collections.emptyList();
        }
    };

}
