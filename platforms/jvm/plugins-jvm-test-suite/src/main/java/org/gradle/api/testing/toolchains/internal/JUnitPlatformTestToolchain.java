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

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework;
import org.gradle.api.tasks.testing.Test;

import javax.inject.Inject;

/**
 * A toolchain for running JUnit Platform tests.
 *
 * @since 8.5
 */
abstract public class JUnitPlatformTestToolchain<T extends JUnitPlatformToolchainParameters> implements JvmTestToolchain<T> {
    /**
     * The default version of the JUnit Platform to use for executing tests.
     */
    public static final String DEFAULT_VERSION = "1.10.0";
    private static final String GROUP_NAME = "org.junit.platform:junit-platform-launcher";

    @Inject
    protected abstract DependencyFactory getDependencyFactory();

    @Override
    public TestFramework createTestFramework(Test task) {
        return new JUnitPlatformTestFramework((DefaultTestFilter) task.getFilter(), false, task.getDryRun());
    }

    @Override
    public Iterable<Dependency> getRuntimeOnlyDependencies() {
        // Use the version of the platform launcher specified in the parameters if present, otherwise assume that the version is provided via a bom
        // referenced by the test engine or otherwise provided in the dependencies.
        return ImmutableSet.of(getDependencyFactory().create(GROUP_NAME + getParameters().getPlatformVersion().map(version -> ":" + version).getOrElse("")));
    }
}
