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

import com.google.common.base.Preconditions;
import org.gradle.api.Action;
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework;
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework;
import org.gradle.api.internal.tasks.testing.testng.TestNGTestFramework;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JvmTestSuitePlugin;
import org.gradle.api.plugins.jvm.JvmComponentDependencies;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.JvmTestSuiteTarget;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskDependency;

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.HashMap;
import java.util.Map;

public abstract class DefaultJvmTestSuite implements JvmTestSuite {
    public enum Frameworks {
        JUNIT4("junit:junit", "4.13"),
        JUNIT_JUPITER("org.junit.jupiter:junit-jupiter", "5.7.2"),
        SPOCK("org.spockframework:spock-core", "2.1-groovy-3.0"),
        KOTLIN_TEST("org.jetbrains.kotlin:kotlin-test-junit", "1.6.21"),
        TESTNG("org.testng:testng", "7.4.0"),
        NONE(null, null);

        @Nullable
        private final String module;
        @Nullable
        private final String defaultVersion;

        Frameworks(@Nullable String module, @Nullable String defaultVersion) {
            Preconditions.checkArgument(module != null && defaultVersion != null || module == null && defaultVersion == null, "Either module and version must both be null, or neither be null.");
            this.module = module;
            this.defaultVersion = defaultVersion;
        }

        @Nullable
        public String getDefaultVersion() {
            return defaultVersion;
        }

        @Nullable
        public String getDependency() {
            return getDependency(getDefaultVersion());
        }

        @Nullable
        public String getDependency(String version) {
            if (null != module) {
                return module + ":" + version;
            } else {
                return null;
            }
        }
    }

    private static class VersionedTestingFramework {
        private final Frameworks type;
        private final String version;

        private VersionedTestingFramework(Frameworks type, String version) {
            Preconditions.checkNotNull(version);
            this.type = type;
            this.version = version;
        }
    }
    private final static VersionedTestingFramework NO_OPINION = new VersionedTestingFramework(Frameworks.NONE, "unset");

    private final ExtensiblePolymorphicDomainObjectContainer<JvmTestSuiteTarget> targets;
    private final SourceSet sourceSet;
    private final String name;
    private final JvmComponentDependencies dependencies;
    private boolean attachedDependencies;
    private final Action<Void> attachDependencyAction;

    protected abstract Property<VersionedTestingFramework> getVersionedTestingFramework();

    @Inject
    public DefaultJvmTestSuite(String name, ConfigurationContainer configurations, DependencyHandler dependencies, SourceSetContainer sourceSets) {
        this.name = name;
        this.sourceSet = sourceSets.create(getName());

        Configuration compileOnly = configurations.getByName(sourceSet.getCompileOnlyConfigurationName());
        Configuration implementation = configurations.getByName(sourceSet.getImplementationConfigurationName());
        Configuration runtimeOnly = configurations.getByName(sourceSet.getRuntimeOnlyConfigurationName());
        Configuration annotationProcessor = configurations.getByName(sourceSet.getAnnotationProcessorConfigurationName());

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
            getVersionedTestingFramework().convention(NO_OPINION);
        }

        this.targets = getObjectFactory().polymorphicDomainObjectContainer(JvmTestSuiteTarget.class);
        this.targets.registerBinding(JvmTestSuiteTarget.class, DefaultJvmTestSuiteTarget.class);

        this.dependencies = getObjectFactory().newInstance(DefaultJvmComponentDependencies.class, implementation, compileOnly, runtimeOnly, annotationProcessor);

        addDefaultTestTarget();

        // Until the values here can be finalized upon the user setting them (see the org.gradle.api.tasks.testing.Test#testFramework(Closure) method),
        // in Gradle 8, we will be executing the provider lambda used as the convention multiple times.  So make sure, within a Test Suite, that we
        // always return the same one via computeIfAbsent() against this map.
        final Map<Frameworks, TestFramework> frameworkLookup = new HashMap<>(3);

        this.targets.withType(JvmTestSuiteTarget.class).configureEach(target -> {
            target.getTestTask().configure(task -> {
                task.getTestFrameworkProperty().convention(getVersionedTestingFramework().map(vtf -> {
                    switch(vtf.type) {
                        case NONE: // fall-through
                        case JUNIT4: // fall-through
                        case KOTLIN_TEST:
                            return frameworkLookup.computeIfAbsent(vtf.type, f -> new JUnitTestFramework(task, (DefaultTestFilter) task.getFilter()));
                        case JUNIT_JUPITER: // fall-through
                        case SPOCK:
                            return frameworkLookup.computeIfAbsent(vtf.type, f -> new JUnitPlatformTestFramework((DefaultTestFilter) task.getFilter()));
                        case TESTNG:
                            return frameworkLookup.computeIfAbsent(vtf.type, f -> new TestNGTestFramework(task, task.getClasspath(), (DefaultTestFilter) task.getFilter(), getObjectFactory()));
                        default:
                            throw new IllegalStateException("do not know how to handle " + vtf);
                    }
                }));
            });
        });
    }

    private void attachDependenciesForTestFramework(DependencyHandler dependencies, Configuration implementation) {
        if (!attachedDependencies) {
            dependencies.addProvider(implementation.getName(), getVersionedTestingFramework().map(framework -> {
                switch (framework.type) {
                    case JUNIT4: // fall-through
                    case JUNIT_JUPITER: // fall-through
                    case SPOCK: // fall-through
                    case TESTNG: // fall-through
                    case KOTLIN_TEST:
                        return framework.type.getDependency(framework.version);
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
        useJUnit(Frameworks.JUNIT4.defaultVersion);
    }

    @Override
    public void useJUnit(String version) {
        setFrameworkTo(new VersionedTestingFramework(Frameworks.JUNIT4, version));
    }

    @Override
    public void useJUnitJupiter() {
        useJUnitJupiter(Frameworks.JUNIT_JUPITER.defaultVersion);
    }

    @Override
    public void useJUnitJupiter(String version) {
        setFrameworkTo(new VersionedTestingFramework(Frameworks.JUNIT_JUPITER, version));
    }

    @Override
    public void useSpock() {
        useSpock(Frameworks.SPOCK.defaultVersion);
    }

    @Override
    public void useSpock(String version) {
        setFrameworkTo(new VersionedTestingFramework(Frameworks.SPOCK, version));
    }

    @Override
    public void useKotlinTest() {
        useKotlinTest(Frameworks.KOTLIN_TEST.defaultVersion);
    }

    @Override
    public void useKotlinTest(String version) {
        setFrameworkTo(new VersionedTestingFramework(Frameworks.KOTLIN_TEST, version));
    }

    @Override
    public void useTestNG() {
        useTestNG(Frameworks.TESTNG.defaultVersion);
    }

    @Override
    public void useTestNG(String version) {
        setFrameworkTo(new VersionedTestingFramework(Frameworks.TESTNG, version));
    }

    private void setFrameworkTo(VersionedTestingFramework framework) {
        getVersionedTestingFramework().set(framework);
        attachDependencyAction.execute(null);
    }

    @Override
    public JvmComponentDependencies getDependencies() {
        return dependencies;
    }

    @Override
    public void dependencies(Action<? super JvmComponentDependencies> action) {
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
