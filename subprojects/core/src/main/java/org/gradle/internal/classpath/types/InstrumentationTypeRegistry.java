/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath.types;

import java.util.Collections;
import java.util.Set;

/**
 * Provides information about types that are visited during instrumentation.
 */
public interface InstrumentationTypeRegistry {

    InstrumentationTypeRegistry EMPTY = new EmptyInstrumentationTypeRegistry();

    /**
     * Returns instrumented Gradle super types for a given type.
     *
     * Note: We currently return just types that uses {@link org.gradle.internal.instrumentation.api.annotations.InterceptInherited}
     * and their subtypes that live in the `org.gradle.` package.
     *
     * TODO: Rename to getInstrumentedSuperTypes();
     */
    Set<String> getSuperTypes(String type);

    boolean isEmpty();

    static InstrumentationTypeRegistry empty() {
        return EMPTY;
    }

    class EmptyInstrumentationTypeRegistry implements InstrumentationTypeRegistry {
        @Override
        public Set<String> getSuperTypes(String type) {
            return Collections.emptySet();
        }

        @Override
        public boolean isEmpty() {
            return true;
        }
    }
}
