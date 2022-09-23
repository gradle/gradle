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
import org.gradle.api.artifacts.ModuleVersionIdentifier;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyAdder;
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
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.testing.Test;
import org.gradle.util.internal.VersionNumber;

import javax.inject.Inject;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
            DefaultModuleVersionIdentifier.newId("org.junit.platform", "junit-platform-launcher", "")
        )),
        // Should be the same as the version listed in the junit-bom corresponding to the default junit-jupiter version.
        JUNIT_PLATFORM("org.junit.platform", "junit-platform-launcher", "1.8.2"),
        SPOCK("org.spockframework", "spock-core", getAppropriateSpockVersion(), Collections.singletonList(
            // spock-core references junit-jupiter's BOM, which in turn specifies the platform version
            DefaultModuleVersionIdentifier.newId("org.junit.platform", "junit-platform-launcher", "")
        )),
        KOTLIN_TEST("org.jetbrains.kotlin", "kotlin-test-junit5", "1.7.10", Collections.singletonList(
            // kotlin-test-junit5 depends on junit-jupiter, which in turn specifies the platform version
            DefaultModuleVersionIdentifier.newId("org.junit.platform", "junit-platform-launcher", "")
        )),
        TESTNG("org.testng", "testng", "7.5");

        private final ModuleVersionIdentifier module;
        private final List<ModuleVersionIdentifier> dependencies;

        TestingFramework(String group, String name, String defaultVersion) {
            this(group, name, defaultVersion, Collections.emptyList());
        }

        TestingFramework(String group, String name, String defaultVersion, List<ModuleVersionIdentifier> dependencies) {
            this.module = DefaultModuleVersionIdentifier.newId(group, name, defaultVersion);
            this.dependencies = dependencies;
        }

        public String getDefaultVersion() {
            return module.getVersion();
        }

        public List<ModuleVersionIdentifier> getImplementationDependencies(String version) {
            return Collections.singletonList(DefaultModuleVersionIdentifier.newId(module.getModule(), version));
        }

        public List<ModuleVersionIdentifier> getRuntimeOnlyDependencies(String version) {
            // In the future we might need a better way to manage versions. Thankfully,
            // JUnit Platform has a BOM, so we don't need to manage versions of
            // these runtime dependencies.
            return dependencies;
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

        public List<ModuleVersionIdentifier> getImplementationDependencies() {
            return framework.getImplementationDependencies(version);
        }

        public List<ModuleVersionIdentifier> getRuntimeOnlyDependencies() {
            return framework.getRuntimeOnlyDependencies(version);
        }
    }

    private final ExtensiblePolymorphicDomainObjectContainer<JvmTestSuiteTarget> targets;
    private final SourceSet sourceSet;
    private final String name;
    private final DependencyFactory dependencyFactory;
    private final JvmComponentDependencies dependencies;

    protected abstract Property<VersionedTestingFramework> getVersionedTestingFramework();

    @Inject
    public DefaultJvmTestSuite(String name, DependencyFactory dependencyFactory, ConfigurationContainer configurations, SourceSetContainer sourceSets) {
        this.name = name;
        this.dependencyFactory = dependencyFactory;
        this.sourceSet = sourceSets.create(getName());

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
            useJUnitJupiter();
        }

        addDefaultTestTarget();

        this.targets.withType(JvmTestSuiteTarget.class).configureEach(target -> {
            target.getTestTask().configure(task -> {

                // By default, our versioned testing framework is whatever the Test says their framework is.
                // However, `setFrameworkTo` will override out versioned testing framework with a `set` call and
                // further `set` the Test's test framework.
                // From now on, the test suite is "in control" of the framework. Before then, the test had complete
                // control over which framework it is using. This allows the users to continue to interact with
                // Test tasks directly and the test suite handles those cases.
                // Regardless of how our versioned testing framework is calculated (either by convention by reading the
                // Test's test framework or via a `setFrameworkTo` call), we will add the proper dependencies to the
                // Test's corresponding source set.
                getVersionedTestingFramework().convention(task.getTestFrameworkProperty().map(framework -> {
                    if (name.equals(JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME)) {
                        return null;
                    } else if (framework instanceof JUnitPlatformTestFramework) {
                        return new VersionedTestingFramework(TestingFramework.JUNIT_PLATFORM, TestingFramework.JUNIT_PLATFORM.getDefaultVersion());
                    } else if (framework instanceof JUnitTestFramework) {
                        return new VersionedTestingFramework(TestingFramework.JUNIT4, TestingFramework.JUNIT4.getDefaultVersion());
                    } else if (framework instanceof TestNGTestFramework) {
                        return new VersionedTestingFramework(TestingFramework.TESTNG, TestingFramework.TESTNG.getDefaultVersion());
                    } else {
                        throw new IllegalStateException("Unknown TestFramework type!" + framework);
                    }
                }));
            });
        });

        implementation.withDependencies(deps -> {
            this.dependencies.getImplementation().bundle(getVersionedTestingFramework().map(vtf ->
                createDependencies(vtf.getImplementationDependencies())).orElse(Collections.emptyList()));
        });
        runtimeOnly.withDependencies(deps -> {
            this.dependencies.getRuntimeOnly().bundle(getVersionedTestingFramework().map(vtf ->
                createDependencies(vtf.getRuntimeOnlyDependencies())).orElse(Collections.emptyList()));
        });
    }

    // Until the values here can be finalized upon the user setting them (see the org.gradle.api.tasks.testing.Test#testFramework(Closure) method),
    // in Gradle 8, we will be executing the provider lambda used as the convention multiple times.  So make sure, within a Test Suite, that we
    // always return the same one via computeIfAbsent() against this map.
    private final Map<TestingFramework, TestFramework> frameworkLookup = new HashMap<>(4);

    private TestFramework getTestFramework(TestingFramework framework, Test task) {
        switch(framework) {
            case JUNIT4:
                return frameworkLookup.computeIfAbsent(framework, f -> new JUnitTestFramework(task, (DefaultTestFilter) task.getFilter(), false));
            case KOTLIN_TEST: // fall-through
            case JUNIT_JUPITER: // fall-through
            case JUNIT_PLATFORM: // fall-through
            case SPOCK:
                return frameworkLookup.computeIfAbsent(framework, f -> new JUnitPlatformTestFramework((DefaultTestFilter) task.getFilter(), false));
            case TESTNG:
                return frameworkLookup.computeIfAbsent(framework, f -> new TestNGTestFramework(task, task.getClasspath(), (DefaultTestFilter) task.getFilter(), getObjectFactory()));
            default:
                throw new IllegalStateException("do not know how to handle " + framework);
        }
    }

    private List<ExternalModuleDependency> createDependencies(List<ModuleVersionIdentifier> dependencies) {
        return dependencies.stream().map(id -> {
            String notation = id.getGroup() + ":" + id.getName() + ("".equals(id.getVersion()) ?  "" : (":" + id.getVersion()));
            return dependencyFactory.create(notation);
        }).collect(Collectors.toList());
    }

    private void setFrameworkTo(TestingFramework framework, Provider<String> version) {
        getVersionedTestingFramework().set(version.map(v -> new VersionedTestingFramework(framework, v)));

        // Only if the user chooses a test framework via the test suites do we
        // override the test framework on the test itself.
        targets.configureEach(target -> target.getTestTask().configure(task -> {
            task.getTestFrameworkProperty().set(getTestFramework(framework, task));
        }));
    }

    @Override
    public String getName() {
        return name;
    }

    @Inject
    public abstract ObjectFactory getObjectFactory();

    @Inject
    public abstract ProviderFactory getProviderFactory();

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
        return new AbstractTaskDependency() {
            @Override
            public void visitDependencies(TaskDependencyResolveContext context) {
                getTargets().forEach(context::add);
            }
        };
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
