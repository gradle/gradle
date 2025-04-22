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
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.tasks.testing.Test;

import javax.inject.Inject;

/**
 * A {@link JvmTestToolchain} for JUnit 4.
 *
 * @since 8.5
 */
abstract public class JUnit4TestToolchain implements JvmTestToolchain<JUnit4ToolchainParameters> {
    /**
     * The default version of JUnit 4 to use for compiling and executing tests.
     */
    public static final String DEFAULT_VERSION = "4.13.2";
    private static final String GROUP_NAME = "junit:junit";

    @Inject
    protected abstract DependencyFactory getDependencyFactory();

    @Inject
    protected abstract ObjectFactory getObjectFactory();

    @Override
    public TestFramework createTestFramework(Test task) {
        return getObjectFactory().newInstance(JUnitTestFramework.class, task.getFilter(), task.getTemporaryDirFactory(), task.getDryRun());
    }

    @Override
    public Iterable<Dependency> getImplementationDependencies() {
        return ImmutableSet.of(getDependencyFactory().create(GROUP_NAME + ":" + getParameters().getVersion().get()));
    }

}
