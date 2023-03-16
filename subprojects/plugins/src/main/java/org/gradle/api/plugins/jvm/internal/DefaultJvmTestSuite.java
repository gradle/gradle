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

import com.google.common.annotations.VisibleForTesting;
import groovy.lang.GroovySystem;
import org.gradle.api.Action;
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyAdder;
import org.gradle.api.internal.tasks.JvmConstants;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework;
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework;
import org.gradle.api.internal.tasks.testing.testng.TestNGTestFramework;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JvmTestSuitePlugin;
import org.gradle.api.plugins.jvm.JvmComponentDependencies;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.JvmTestSuiteTarget;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.testing.Test;
import org.gradle.util.internal.VersionNumber;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public abstract class DefaultJvmTestSuite implements JvmTestSuite {
    /**
     * Dependency information and default versions for supported testing frameworks.
     * When updating these versions, be sure to update the default versions noted in `JvmTestSuite` javadoc
     */
    @VisibleForTesting
    public enum TestingFramework {
        JUNIT4("junit", "junit", "4.13.2"),
        JUNIT_JUPITER("org.junit.jupiter", "junit-jupiter", "5.8.2", Collections.singletonList(
                // junit-jupiter's BOM, junit-bom, specifies the platform version
                "org.junit.platform:junit-platform-launcher"
        )),
        SPOCK("org.spockframework", "spock-core", getAppropriateSpockVersion(), Collections.singletonList(
                // spock-core references junit-jupiter's BOM, which in turn specifies the platform version
                "org.junit.platform:junit-platform-launcher"
        )),
        KOTLIN_TEST("org.jetbrains.kotlin", "kotlin-test-junit5", "1.8.10", Collections.singletonList(
                // kotlin-test-junit5 depends on junit-jupiter, which in turn specifies the platform version
                "org.junit.platform:junit-platform-launcher"
        )),
        TESTNG("org.testng", "testng", "7.5");

        private final String groupName;

        private final String defaultVersion;
        private final List<String> runtimeDependencies;

        TestingFramework(String group, String name, String defaultVersion) {
            this(group, name, defaultVersion, Collections.emptyList());
        }

        TestingFramework(String group, String name, String defaultVersion, List<String> runtimeDependencies) {
            this.groupName = group + ":" + name;
            this.defaultVersion = defaultVersion;
            this.runtimeDependencies = runtimeDependencies;
        }

        public String getDefaultVersion() {
            return defaultVersion;
        }

        public List<String> getImplementationDependencies(String version) {
            return Collections.singletonList(groupName + ":" + version);
        }

        public List<String> getRuntimeOnlyDependencies() {
            // In the future we might need a better way to manage versions. Thankfully,
            // JUnit Platform has a BOM, so we don't need to manage versions of
            // these runtime dependencies.
            return runtimeDependencies;
        }

        /**
         * TODO update javadoc on {@link JvmTestSuite#useSpock()} to indicate that
         *  2.2-groovy-4.0 becomes the new default once Groovy 4 is permanently enabled.
         *
         * @return a spock version compatible spock with the bundled Groovy version
         */
        private static String getAppropriateSpockVersion() {
            if (VersionNumber.parse(GroovySystem.getVersion()).getMajor() >= 4) {
                return "2.2-groovy-4.0";
            }
            return "2.2-groovy-3.0";
        }
    }

    private static class VersionedTestingFramework {
        private final TestingFramework framework;
        private final String version;

        private VersionedTestingFramework(TestingFramework framework, String version) {
            this.framework = framework;
            this.version = version;
        }

        public TestingFramework getFramework() {
            return framework;
        }

        public List<String> getImplementationDependencies() {
            return framework.getImplementationDependencies(version);
        }
        public List<String> getRuntimeOnlyDependencies() {
            return framework.getRuntimeOnlyDependencies();
        }
    }

    private final ExtensiblePolymorphicDomainObjectContainer<JvmTestSuiteTarget> targets;
    private final SourceSet sourceSet;
    private final String name;
    private final JvmComponentDependencies dependencies;
    private final TaskDependencyFactory taskDependencyFactory;

    @Inject
    public DefaultJvmTestSuite(String name, SourceSetContainer sourceSets, ConfigurationContainer configurations, TaskDependencyFactory taskDependencyFactory) {
        this.name = name;
        this.sourceSet = sourceSets.create(getName());
        this.taskDependencyFactory = taskDependencyFactory;

        Configuration compileOnly = configurations.getByName(sourceSet.getCompileOnlyConfigurationName());
        Configuration implementation = configurations.getByName(sourceSet.getImplementationConfigurationName());
        Configuration runtimeOnly = configurations.getByName(sourceSet.getRuntimeOnlyConfigurationName());
        Configuration annotationProcessor = configurations.getByName(sourceSet.getAnnotationProcessorConfigurationName());

        this.targets = getObjectFactory().polymorphicDomainObjectContainer(JvmTestSuiteTarget.class);
        this.targets.registerBinding(JvmTestSuiteTarget.class, DefaultJvmTestSuiteTarget.class);

        this.dependencies = getObjectFactory().newInstance(
                DefaultJvmComponentDependencies.class,
                getObjectFactory().newInstance(DefaultDependencyAdder.class, implementation),
                getObjectFactory().newInstance(DefaultDependencyAdder.class, compileOnly),
                getObjectFactory().newInstance(DefaultDependencyAdder.class, runtimeOnly),
                getObjectFactory().newInstance(DefaultDependencyAdder.class, annotationProcessor)
        );

        if (!name.equals(JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME)) {
            // for the built-in test suite, we don't express an opinion, so we will not add any dependencies
            // if a user explicitly calls useJUnit or useJUnitJupiter, the built-in test suite will behave like a custom one
            // and add dependencies automatically.
            getTestSuiteTestingFramework().convention(new VersionedTestingFramework(TestingFramework.JUNIT_JUPITER, TestingFramework.JUNIT_JUPITER.getDefaultVersion()));
        }
        getTestSuiteTestingFramework().finalizeValueOnRead(); // The framework set on the SUITE is finalized upon read, even for the default suite

        addDefaultTestTarget();

        this.targets.withType(JvmTestSuiteTarget.class).configureEach(target -> {
            target.getTestTask().configure(task -> {
                initializeTestFramework(name, task);
            });
        });

        // This is a workaround for strange behavior from the Kotlin plugin
        //
        // The Kotlin plugin attempts to look at the declared dependencies to know if it needs to add its own dependencies.
        // We avoid triggering realization of getTestSuiteTestingFramework by only adding our dependencies just before
        // resolution.
        implementation.withDependencies(dependencySet -> {
            this.dependencies.getImplementation().bundle(getTestSuiteTestingFramework().map(vtf -> createDependencies(vtf.getImplementationDependencies())).orElse(Collections.emptyList()));
        });
        runtimeOnly.withDependencies(dependencySet -> {
            this.dependencies.getRuntimeOnly().bundle(getTestSuiteTestingFramework().map(vtf -> createDependencies(vtf.getRuntimeOnlyDependencies())).orElse(Collections.emptyList()));
        });
    }

    private void initializeTestFramework(String name, Test task) {
        Provider<TestFramework> mapTestingFrameworkToTestFramework = getTestSuiteTestingFramework().map(vtf -> {
            switch (vtf.getFramework()) {
                case JUNIT4:
                    return new JUnitTestFramework(task, (DefaultTestFilter) task.getFilter(), false);
                case KOTLIN_TEST: // fall-through
                case JUNIT_JUPITER: // fall-through
                case SPOCK:
                    return new JUnitPlatformTestFramework((DefaultTestFilter) task.getFilter(), false);
                case TESTNG:
                    return new TestNGTestFramework(task, (DefaultTestFilter) task.getFilter(), getObjectFactory());
                default:
                    throw new IllegalStateException("do not know how to handle " + vtf);
            }
        });

        if (name.equals(JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME)) {
            // In order to maintain compatibility for the default test suite, we need to load JUnit4 from the Gradle distribution
            // instead of including it in testImplementation.
            task.getTestFrameworkProperty().convention(mapTestingFrameworkToTestFramework.orElse(getProviderFactory().provider(() -> new JUnitTestFramework(task, (DefaultTestFilter) task.getFilter(), true))));
            // We can't disallow changes to the test framework yet because we need to allow the test task to be configured without the test suite
            task.getTestFrameworkProperty().finalizeValueOnRead();
        } else {
            // The Test task's testing framework is derived from the test suite's testing framework
            task.getTestFrameworkProperty().convention(mapTestingFrameworkToTestFramework);
            // The Test task cannot override the testing framework chosen by the test suite
            task.getTestFrameworkProperty().disallowChanges();
            // The Test task's testing framework is locked in as soon as its needed
            task.getTestFrameworkProperty().finalizeValueOnRead();
        }
    }

    private void addDefaultTestTarget() {
        final String target;
        if (getName().equals(JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME)) {
            target = JvmConstants.TEST_TASK_NAME;
        } else {
            target = getName(); // For now, we'll just name the test task for the single target for the suite with the suite name
        }

        targets.register(target);
    }
    protected abstract Property<VersionedTestingFramework> getTestSuiteTestingFramework();

    private List<ExternalModuleDependency> createDependencies(List<String> dependencies) {
        return dependencies.stream().map(getDependencyFactory()::create).collect(Collectors.toList());
    }

    private void setFrameworkTo(TestingFramework framework, Provider<String> version) {
        getTestSuiteTestingFramework().set(version.map(v -> new VersionedTestingFramework(framework, v)));
    }

    @Override
    public String getName() {
        return name;
    }

    @Inject
    public abstract ObjectFactory getObjectFactory();

    @Inject
    public abstract ProviderFactory getProviderFactory();

    @Inject
    public abstract DependencyFactory getDependencyFactory();

    @Override
    public SourceSet getSources() {
        return sourceSet;
    }

    @Override
    public void sources(Action<? super SourceSet> configuration) {
        configuration.execute(getSources());
    }

    @Override
    public ExtensiblePolymorphicDomainObjectContainer<JvmTestSuiteTarget> getTargets() {
        return targets;
    }

    @Override
    public void useJUnit() {
        useJUnit(TestingFramework.JUNIT4.getDefaultVersion());
    }

    @Override
    public void useJUnit(String version) {
        useJUnit(getProviderFactory().provider(() -> version));
    }

    @Override
    public void useJUnit(Provider<String> version) {
        setFrameworkTo(TestingFramework.JUNIT4, version);
    }

    @Override
    public void useJUnitJupiter() {
        useJUnitJupiter(TestingFramework.JUNIT_JUPITER.getDefaultVersion());
    }

    @Override
    public void useJUnitJupiter(String version) {
        useJUnitJupiter(getProviderFactory().provider(() -> version));
    }

    @Override
    public void useJUnitJupiter(Provider<String> version) {
        setFrameworkTo(TestingFramework.JUNIT_JUPITER, version);
    }

    @Override
    public void useSpock() {
        useSpock(TestingFramework.SPOCK.getDefaultVersion());
    }

    @Override
    public void useSpock(String version) {
        useSpock(getProviderFactory().provider(() -> version));
    }

    @Override
    public void useSpock(Provider<String> version) {
        setFrameworkTo(TestingFramework.SPOCK, version);
    }

    @Override
    public void useKotlinTest() {
        useKotlinTest(TestingFramework.KOTLIN_TEST.getDefaultVersion());
    }

    @Override
    public void useKotlinTest(String version) {
        useKotlinTest(getProviderFactory().provider(() -> version));
    }

    @Override
    public void useKotlinTest(Provider<String> version) {
        setFrameworkTo(TestingFramework.KOTLIN_TEST, version);
    }

    @Override
    public void useTestNG() {
        useTestNG(TestingFramework.TESTNG.getDefaultVersion());
    }

    @Override
    public void useTestNG(String version) {
        useTestNG(getProviderFactory().provider(() -> version));
    }

    @Override
    public void useTestNG(Provider<String> version) {
        setFrameworkTo(TestingFramework.TESTNG, version);
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
        return taskDependencyFactory.visitingDependencies(context -> {
            getTargets().forEach(context::add);
        });
    }
}
