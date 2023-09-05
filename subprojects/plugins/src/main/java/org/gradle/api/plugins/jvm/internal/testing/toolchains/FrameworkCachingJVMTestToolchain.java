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

import com.google.common.collect.Maps;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.plugins.jvm.testing.toolchains.JVMTestToolchainParameters;
import org.gradle.api.tasks.testing.Test;
import org.gradle.util.Path;

import java.util.Map;

public class FrameworkCachingJVMTestToolchain <T extends JVMTestToolchainParameters> implements JVMTestToolchain<T> {
    private final JVMTestToolchain<T> delegate;
    private final Map<Path, TestFramework> testFrameworks = Maps.newHashMap();

    public FrameworkCachingJVMTestToolchain(JVMTestToolchain<T> delegate) {
        this.delegate = delegate;
    }

    @Override
    public TestFramework createTestFramework(Test task) {
        return testFrameworks.computeIfAbsent(task.getIdentityPath(), k -> delegate.createTestFramework(task));
    }

    @Override
    public Iterable<Dependency> getCompileOnlyDependencies() {
        return delegate.getCompileOnlyDependencies();
    }

    @Override
    public Iterable<Dependency> getRuntimeOnlyDependencies() {
        return delegate.getRuntimeOnlyDependencies();
    }

    @Override
    public Iterable<Dependency> getImplementationDependencies() {
        return delegate.getImplementationDependencies();
    }

    @Override
    public T getParameters() {
        return delegate.getParameters();
    }
}
