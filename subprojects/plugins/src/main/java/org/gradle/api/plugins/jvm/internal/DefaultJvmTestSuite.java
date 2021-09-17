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

import org.gradle.api.Action;
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JvmTestSuitePlugin;
import org.gradle.api.plugins.jvm.ComponentDependencies;
import org.gradle.api.plugins.jvm.JUnitPlatformTestingFramework;
import org.gradle.api.plugins.jvm.JUnitTestingFramework;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.JvmTestSuiteTarget;
import org.gradle.api.plugins.jvm.JvmTestingFramework;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskDependency;

import javax.inject.Inject;

public abstract class DefaultJvmTestSuite implements JvmTestSuite {
    private final ExtensiblePolymorphicDomainObjectContainer<JvmTestSuiteTarget> targets;
    private final SourceSet sourceSet;
    private final String name;
    private final ComponentDependencies dependencies;

    @Inject
    public DefaultJvmTestSuite(String name, ConfigurationContainer configurations, DependencyHandler dependencies, SourceSetContainer sourceSets) {
        this.name = name;
        this.sourceSet = sourceSets.create(getName());

        Configuration compileOnly = configurations.getByName(sourceSet.getCompileOnlyConfigurationName());
        Configuration implementation = configurations.getByName(sourceSet.getImplementationConfigurationName());
        Configuration runtimeOnly = configurations.getByName(sourceSet.getRuntimeOnlyConfigurationName());

        if (!getName().equals(JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME)) {
            dependencies.addProvider(implementation.getName(), getTestingFramework().map(framework -> {
                if (framework instanceof JUnitPlatformTestingFramework) {
                    return "org.junit.jupiter:junit-jupiter-api:" + framework.getVersion().get();
                } else {
                    return "junit:junit:" + framework.getVersion().get();
                }
            }));
            dependencies.addProvider(runtimeOnly.getName(), getTestingFramework().map(framework -> {
                if (framework instanceof JUnitPlatformTestingFramework) {
                    return "org.junit.jupiter:junit-jupiter-engine:" + framework.getVersion().get();
                } else {
                    return getObjectFactory().fileCollection();
                }
            }));
        }

        this.targets = getObjectFactory().polymorphicDomainObjectContainer(JvmTestSuiteTarget.class);
        this.targets.registerBinding(JvmTestSuiteTarget.class, DefaultJvmTestSuiteTarget.class);

        this.dependencies = getObjectFactory().newInstance(DefaultComponentDependencies.class, implementation, compileOnly, runtimeOnly);
        addDefaultTestTarget();
    }

    @Override
    public String getName() {
        return name;
    }

    @Inject
    public abstract ObjectFactory getObjectFactory();

    @Inject
    public abstract ProviderFactory getProviders();

    public SourceSet getSources() {
        return sourceSet;
    }
    public void sources(Action<? super SourceSet> configuration) {
        configuration.execute(getSources());
    }

    public ExtensiblePolymorphicDomainObjectContainer<JvmTestSuiteTarget> getTargets() {
        return targets;
    }

    public void addDefaultTestTarget() {
        final String target;
        if (getName().equals(JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME)) {
            target = JavaPlugin.TEST_TASK_NAME;
        } else {
            target = getName(); // For now, we'll just name the test task for the single target for the suite with the suite name
        }

        targets.register(target);
    }

    @Override
    public void useJUnit() {
        JvmTestingFramework testingFramework = getObjectFactory().newInstance(JUnitTestingFramework.class);
        getTestingFramework().convention(testingFramework);
        testingFramework.getVersion().convention("4.13");
    }

    @Override
    public void useJUnitPlatform() {
        JvmTestingFramework testingFramework = getObjectFactory().newInstance(JUnitPlatformTestingFramework.class);
        getTestingFramework().convention(testingFramework);
        testingFramework.getVersion().convention("5.7.1");
    }

    @Override
    public ComponentDependencies getDependencies() {
        return dependencies;
    }

    @Override
    public void dependencies(Action<? super ComponentDependencies> action) {
        action.execute(dependencies);
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return new AbstractTaskDependency() {
            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                getTargets().forEach(context::add);
            }
        };
    }
}
