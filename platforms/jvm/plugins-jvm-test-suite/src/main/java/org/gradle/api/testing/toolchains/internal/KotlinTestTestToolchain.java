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

package org.gradle.api.testing.toolchains.internal;

import org.gradle.api.artifacts.Dependency;

import java.util.Collections;

/**
 * A {@link JUnitPlatformTestToolchain} that uses the KotlinTest test engine.
 *
 * @since 8.5
 */
abstract public class KotlinTestTestToolchain extends JUnitPlatformTestToolchain<KotlinTestToolchainParameters> {
    /**
     * The default version of KotlinTest to use for compiling and executing tests.
     */
    public static final String DEFAULT_VERSION = "1.9.23";
    private static final String GROUP_NAME = "org.jetbrains.kotlin:kotlin-test-junit5";

    @Override
    public Iterable<Dependency> getImplementationDependencies() {
        return Collections.singletonList(getDependencyFactory().create(GROUP_NAME + ":" + getParameters().getKotlinTestVersion().get()));
    }
}
