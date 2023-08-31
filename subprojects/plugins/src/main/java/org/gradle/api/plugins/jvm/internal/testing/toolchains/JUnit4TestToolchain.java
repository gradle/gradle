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

import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.testing.Test;

import javax.inject.Inject;
import java.util.Collections;

abstract public class JUnit4TestToolchain extends AbstractJVMTestToolchain<JUnit4TestToolchain.Parameters> {
    public static final String DEFAULT_VERSION = "4.13.2";
    private static final String GROUP_NAME = "junit:junit";

    @Inject
    protected abstract DependencyFactory getDependencyFactory();

    @Override
    public TestFramework initializeTestFramework(Test task) {
        return new JUnitTestFramework(task, (DefaultTestFilter) task.getFilter(), false);
    }

    @Override
    public Iterable<Dependency> getCompileOnlyDependencies() {
        return Collections.emptyList();
    }

    @Override
    public Iterable<Dependency> getRuntimeOnlyDependencies() {
        return Collections.emptyList();
    }

    @Override
    public Iterable<Dependency> getImplementationDependencies() {
        return Collections.singletonList(getDependencyFactory().create(GROUP_NAME + ":" + getParameters().getVersion().get()));
    }

    public interface Parameters extends JVMTestToolchain.Parameters {
        Property<String> getVersion();
    }
}
