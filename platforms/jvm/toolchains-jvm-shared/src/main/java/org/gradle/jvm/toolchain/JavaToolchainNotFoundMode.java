/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.jvm.toolchain;

import org.gradle.api.Incubating;

/**
 * Describes how to handle missing {@link JavaToolchainSpec}.
 *
 * @see JavaToolchainSpec#getOnNoMatchFound
 * @since 9.5.0
 */
@Incubating
public enum JavaToolchainNotFoundMode {
    /**
     * If no toolchain matching a given {@link JavaToolchainSpec} is found, failures will be ignored.
     *
     * @since 9.5.0
     */
    IGNORE,

    /**
     * If no toolchain matching a given {@link JavaToolchainSpec} can be found, a failure will be thrown.
     *
     * @since 9.5.0
     */
    THROW_EXCEPTION,
}
