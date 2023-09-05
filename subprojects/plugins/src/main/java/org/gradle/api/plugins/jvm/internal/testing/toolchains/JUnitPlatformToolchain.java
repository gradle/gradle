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
import com.google.common.collect.Streams;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework;
import org.gradle.api.plugins.jvm.internal.testing.engines.JUnitPlatformTestEngine;
import org.gradle.api.plugins.jvm.testing.toolchains.JUnitPlatformToolchainParameters;
import org.gradle.api.tasks.testing.Test;

import javax.inject.Inject;
import java.util.function.Function;
import java.util.stream.Collectors;

abstract public class JUnitPlatformToolchain<T extends JUnitPlatformToolchainParameters> implements JVMTestToolchain<T> {
    public static final String DEFAULT_VERSION = "1.10.0";
    private static final String GROUP_NAME = "org.junit.platform:junit-platform-launcher";

    @Override
    public TestFramework createTestFramework(Test task) {
        return new JUnitPlatformTestFramework((DefaultTestFilter) task.getFilter(), false, task.getDryRun());
    }

    @Override
    public Iterable<Dependency> getCompileOnlyDependencies() {
        return ImmutableSet.copyOf(dependenciesFrom(JUnitPlatformTestEngine::getCompileOnlyDependencies));
    }

    @Override
    public Iterable<Dependency> getRuntimeOnlyDependencies() {
        ImmutableSet.Builder<Dependency> builder = ImmutableSet.builder();
        builder.addAll(dependenciesFrom(JUnitPlatformTestEngine::getRuntimeOnlyDependencies));
        builder.add(getDependencyFactory().create(GROUP_NAME + getParameters().getPlatformVersion().map(version -> ":" + version).getOrElse("")));
        return builder.build();
    }

    @Override
    public Iterable<Dependency> getImplementationDependencies() {
        return ImmutableSet.copyOf(dependenciesFrom(JUnitPlatformTestEngine::getImplementationDependencies));
    }

    private Iterable<Dependency> dependenciesFrom(Function<? super JUnitPlatformTestEngine<?>, Iterable<Dependency>> dependencyFunction) {
        return getParameters().getEngines().get().stream()
            .map(dependencyFunction)
            .flatMap(Streams::stream)
            .collect(Collectors.toSet());
    }

    @Inject
    protected abstract DependencyFactory getDependencyFactory();

}
