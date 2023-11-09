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

package org.gradle.internal.instrumentation.api.capabilities;

public enum InterceptionType {

    /**
     * An interceptor that is applied to the bytecode always.
     */
    INSTRUMENTATION(InterceptionCapability.InstrumentationInterceptor.class),

    /**
     * An interceptor that is applied to the bytecode only for upgrades.
     */
    BYTECODE_UPGRADE(InterceptionCapability.BytecodeUpgradeInterceptor.class);

    private final Class<? extends InterceptionCapability> markerInterface;

    InterceptionType(Class<? extends InterceptionCapability> markerInterface) {
        this.markerInterface = markerInterface;
    }

    public Class<? extends InterceptionCapability> getMarkerInterface() {
        return markerInterface;
    }
}
