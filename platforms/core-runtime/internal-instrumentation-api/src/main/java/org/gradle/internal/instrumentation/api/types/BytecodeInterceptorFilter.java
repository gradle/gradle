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

package org.gradle.internal.instrumentation.api.types;

import java.util.EnumSet;

/**
 * Request for interceptors. Currently, we support filter just for interception types, but in the feature we could also filter per version etc.
 *
 * Implemented as a enum, so it's easier to generate bytecode.
 */
public enum BytecodeInterceptorFilter {
    INSTRUMENTATION_ONLY(EnumSet.of(BytecodeInterceptorType.INSTRUMENTATION)),
    ALL(EnumSet.of(BytecodeInterceptorType.INSTRUMENTATION, BytecodeInterceptorType.BYTECODE_UPGRADE));

    @SuppressWarnings("ImmutableEnumChecker")
    private final EnumSet<BytecodeInterceptorType> instrumentationTypes;

    BytecodeInterceptorFilter(EnumSet<BytecodeInterceptorType> instrumentationTypes) {
        this.instrumentationTypes = instrumentationTypes;
    }

    public boolean matches(FilterableBytecodeInterceptor bytecodeInterceptor) {
        return instrumentationTypes.contains(bytecodeInterceptor.getType());
    }

    public boolean matches(FilterableBytecodeInterceptorFactory bytecodeInterceptorFactory) {
        return instrumentationTypes.contains(bytecodeInterceptorFactory.getType());
    }
}
