/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.plugins.jvm.internal;

import org.gradle.api.Project;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.dsl.DependencyAdder;
import org.gradle.api.internal.artifacts.dsl.dependencies.DependencyFactoryInternal;
import org.gradle.api.plugins.jvm.JvmComponentDependencies;
import org.gradle.internal.component.external.model.ImmutableCapability;
import org.gradle.internal.component.external.model.ProjectTestFixtures;

import javax.inject.Inject;

import static org.gradle.internal.component.external.model.TestFixturesSupport.TEST_FIXTURES_CAPABILITY_APPENDIX;

public class DefaultJvmComponentDependencies implements JvmComponentDependencies {
    private final DependencyAdder implementation;
    private final DependencyAdder compileOnly;
    private final DependencyAdder runtimeOnly;
    private final DependencyAdder annotationProcessor;

    @Inject
    public DefaultJvmComponentDependencies(DependencyAdder implementation, DependencyAdder compileOnly, DependencyAdder runtimeOnly, DependencyAdder annotationProcessor) {
        this.implementation = implementation;
        this.compileOnly = compileOnly;
        this.runtimeOnly = runtimeOnly;
        this.annotationProcessor = annotationProcessor;
    }

    @Inject
    protected DependencyFactoryInternal getDependencyFactory() {
        throw new UnsupportedOperationException();
    }

    @Override
    public Dependency gradleApi() {
        return getDependencyFactory().createDependency(DependencyFactoryInternal.ClassPathNotation.GRADLE_API);
    }

    @Override
    public Dependency gradleTestKit() {
        return getDependencyFactory().createDependency(DependencyFactoryInternal.ClassPathNotation.GRADLE_TEST_KIT);
    }

    @Override
    public Dependency localGroovy() {
        return getDependencyFactory().createDependency(DependencyFactoryInternal.ClassPathNotation.LOCAL_GROOVY);
    }

    public Dependency testFixtures(Project project) {
        final ProjectDependency projectDependency = getDependencyFactory().create(project);
        return testFixtures(projectDependency);
    }

    @Override
    public Dependency testFixtures(ProjectDependency projectDependency) {
        projectDependency.capabilities(new ProjectTestFixtures(projectDependency.getDependencyProject()));
        return projectDependency;
    }

    @Override
    public Dependency testFixtures(ModuleDependency moduleDependency) {
        moduleDependency.capabilities(capabilities -> {
            capabilities.requireCapability(new ImmutableCapability(moduleDependency.getGroup(), moduleDependency.getName() + TEST_FIXTURES_CAPABILITY_APPENDIX, null));
        });
        return moduleDependency;
    }

    @Override
    public DependencyAdder getImplementation() {
        return this.implementation;
    }

    @Override
    public DependencyAdder getCompileOnly() {
        return this.compileOnly;
    }

    @Override
    public DependencyAdder getRuntimeOnly() {
        return this.runtimeOnly;
    }

    @Override
    public DependencyAdder getAnnotationProcessor() {
        return this.annotationProcessor;
    }

}
