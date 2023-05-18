/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.jvm.toolchain.internal;

import org.gradle.jvm.toolchain.JavaToolchainSpec;

public interface JavaToolchainSpecInternal extends JavaToolchainSpec {

    /**
     * A key corresponding to the spec that is an immutable snapshot of the spec properties
     * suitable for usage in collections.
     */
    interface Key {}

    Key toKey();

    /**
     * A spec is considered configured when at least {@link #getLanguageVersion() language version} is set.
     * <p>
     * A spec that is not configured always directly matches the toolchain of the current JVM.
     */
    boolean isConfigured();

    /**
     * A spec is valid when {@link #getLanguageVersion() language version} is set (along with any other properties)
     * or when no properties are set.
     * <p>
     * A {@link #isConfigured() non-configured} spec is always valid.
     */
    boolean isValid();

    /**
     * Finalizes values of all spec properties, disallowing any further changes.
     */
    void finalizeProperties();

}
