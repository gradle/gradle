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

public interface FunctionSemantics extends Serializable {

    DataTypeRef getReturnValueType();

    default boolean isNewObjectFunctionSemantics() {
        return false;
    }

    default boolean isConfigureSemantics() {
        return false;
    }

    default DataTypeRef getConfiguredType() {
        throw new UnsupportedOperationException("Not configure semantics");
    }

    default ConfigureBlockRequirement getConfigureBlockRequirement() {
        throw new UnsupportedOperationException("Not configure semantics");
    }

    enum ConfigureBlockRequirement {
        NOT_ALLOWED, OPTIONAL, REQUIRED;

        public boolean allows() {
            return this != NOT_ALLOWED;
        }

        public boolean requires() {
            return this == REQUIRED;
        }

        public boolean isValidIfLambdaIsPresent(boolean isPresent) {
            return isPresent ? allows() : !requires();
        }

    }

}
