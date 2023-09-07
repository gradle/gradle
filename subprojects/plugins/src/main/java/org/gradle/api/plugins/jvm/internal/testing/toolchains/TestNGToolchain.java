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

package org.gradle.api.plugins.jvm.internal.testing.toolchains;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.testng.TestNGTestFramework;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.jvm.testing.toolchains.TestNGToolchainParameters;
import org.gradle.api.tasks.testing.Test;

import javax.inject.Inject;

abstract public class TestNGToolchain implements JVMTestToolchain<TestNGToolchainParameters> {
    public static final String DEFAULT_VERSION = "7.5";
    private static final String GROUP_NAME = "org.testng:testng";

    @Inject
    abstract protected DependencyFactory getDependencyFactory();

    @Inject
    abstract protected ObjectFactory getObjectFactory();

    @Override
    public TestFramework createTestFramework(Test task) {
        return new TestNGTestFramework(task, (DefaultTestFilter) task.getFilter(), getObjectFactory());
    }

    @Override
    public Iterable<Dependency> getImplementationDependencies() {
        return ImmutableSet.of(getDependencyFactory().create(GROUP_NAME + ":" + getParameters().getVersion().get()));
    }

}
