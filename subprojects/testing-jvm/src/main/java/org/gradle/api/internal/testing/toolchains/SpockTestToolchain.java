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

package org.gradle.api.internal.testing.toolchains;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.Dependency;

/**
 * A {@link JUnitPlatformTestToolchain} that uses the Spock test engine.
 *
 * @since 8.5
 */
abstract public class SpockTestToolchain extends JUnitPlatformTestToolchain<SpockToolchainParameters> {
    public static final String DEFAULT_VERSION = "2.2-groovy-3.0";
    private static final String GROUP_NAME = "org.spockframework:spock-core";

    @Override
    public Iterable<Dependency> getImplementationDependencies() {
        return ImmutableSet.of(getDependencyFactory().create(GROUP_NAME + ":" + getParameters().getSpockVersion().get()));
    }
}
