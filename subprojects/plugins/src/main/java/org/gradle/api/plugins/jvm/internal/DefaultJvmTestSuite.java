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
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework;
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JvmTestSuitePlugin;
import org.gradle.api.plugins.jvm.ComponentDependencies;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.JvmTestSuiteTarget;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskDependency;

import javax.annotation.Nullable;
import javax.inject.Inject;

public abstract class DefaultJvmTestSuite implements JvmTestSuite {
    private enum Frameworks {
        JUNIT4, JUNIT_JUPITER, NONE;
    }
    private static class TestingFramework {
        private final Frameworks framework;
        @Nullable
        private final String version;

        private TestingFramework(Frameworks framework, @Nullable String version) {
            this.framework = framework;
            this.version = version;
        }
    }

    private final ExtensiblePolymorphicDomainObjectContainer<JvmTestSuiteTarget> targets;
    private final SourceSet sourceSet;
    private final String name;
    private final ComponentDependencies dependencies;
    private boolean attachedDependencies;
    private final Action<Void> attachDependencyAction;

    protected abstract Property<TestingFramework> getTestingFramework();

    @Inject
    public DefaultJvmTestSuite(String name, ConfigurationContainer configurations, DependencyHandler dependencies, SourceSetContainer sourceSets) {
        this.name = name;
        this.sourceSet = sourceSets.create(getName());

        Configuration compileOnly = configurations.getByName(sourceSet.getCompileOnlyConfigurationName());
        Configuration implementation = configurations.getByName(sourceSet.getImplementationConfigurationName());
        Configuration runtimeOnly = configurations.getByName(sourceSet.getRuntimeOnlyConfigurationName());

        this.attachedDependencies = false;
        // This complexity is to keep the built-in test suite from automatically adding dependencies
        // unless a user explicitly calls one of the useXXX methods
        // Eventually, we should deprecate this behavior and provide a way for users to opt out
        // We could then always add these dependencies.
        this.attachDependencyAction = x -> attachDependenciesForTestFramework(dependencies, implementation);

        if (!name.equals(JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME)) {
            useJUnitJupiter();
        } else {
            // for the built-in test suite, we don't express an opinion, so we will not add any dependencies
            // if a user explicitly calls useJUnit or useJUnitJupiter, the built-in test suite will behave like a custom one
            // and add dependencies automatically.
            getTestingFramework().convention(new TestingFramework(Frameworks.NONE, null));
        }

        this.targets = getObjectFactory().polymorphicDomainObjectContainer(JvmTestSuiteTarget.class);
        this.targets.registerBinding(JvmTestSuiteTarget.class, DefaultJvmTestSuiteTarget.class);

        this.dependencies = getObjectFactory().newInstance(DefaultComponentDependencies.class, implementation, compileOnly, runtimeOnly);

        addDefaultTestTarget();

        this.targets.withType(JvmTestSuiteTarget.class).configureEach(target -> {
            target.getTestTask().configure(task -> {
                task.getTestFrameworkProperty().convention(getTestingFramework().map(framework -> {
                    switch(framework.framework) {
                        case NONE:
                        case JUNIT4:
                            return new JUnitTestFramework(task, (DefaultTestFilter) task.getFilter());
                        case JUNIT_JUPITER:
                            return new JUnitPlatformTestFramework((DefaultTestFilter) task.getFilter());
                        default:
                            throw new IllegalStateException("do not know how to handle " + framework);
                    }
                }));
            });
        });

    }

    private void attachDependenciesForTestFramework(DependencyHandler dependencies, Configuration implementation) {
        if (!attachedDependencies) {
            dependencies.addProvider(implementation.getName(), getTestingFramework().map(framework -> {
                switch (framework.framework) {
                    case JUNIT4:
                        assert framework.version != null;
                        return "junit:junit:" + framework.version;
                    case JUNIT_JUPITER:
                        assert framework.version != null;
                        return "org.junit.jupiter:junit-jupiter:" + framework.version;
                    default:
                        throw new IllegalStateException("do not know how to handle " + framework);
                }
            }));
            attachedDependencies = true;
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Inject
    public abstract ObjectFactory getObjectFactory();

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
        useJUnit("4.13");
    }

    @Override
    public void useJUnit(String version) {
        setFrameworkTo(new TestingFramework(Frameworks.JUNIT4, version));
    }

    private void setFrameworkTo(TestingFramework framework) {
        getTestingFramework().set(framework);
        attachDependencyAction.execute(null);
    }

    @Override
    public void useJUnitJupiter() {
        useJUnitJupiter("5.7.1");
    }

    @Override
    public void useJUnitJupiter(String version) {
        setFrameworkTo(new TestingFramework(Frameworks.JUNIT_JUPITER, version));
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
