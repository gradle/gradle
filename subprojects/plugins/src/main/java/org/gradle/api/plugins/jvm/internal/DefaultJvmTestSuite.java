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
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.ExternalModuleDependency;
import org.gradle.api.artifacts.dsl.DependencyAdder;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyAdder;
import org.gradle.api.internal.tasks.JvmConstants;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.internal.tasks.testing.TestFramework;
import org.gradle.api.internal.tasks.testing.filter.DefaultTestFilter;
import org.gradle.api.internal.tasks.testing.junit.JUnitTestFramework;
import org.gradle.api.internal.tasks.testing.junitplatform.JUnitPlatformTestFramework;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JvmTestSuitePlugin;
import org.gradle.api.plugins.jvm.JvmComponentDependencies;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.JvmTestSuiteTarget;
import org.gradle.api.plugins.jvm.TestEngine;
import org.gradle.api.plugins.jvm.TestEngineContainer;
import org.gradle.api.plugins.jvm.TestEngineParametersContainer;
import org.gradle.api.provider.Provider;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.testing.engines.jupiter.JUnitJupiterTestEngine;
import org.gradle.util.internal.VersionNumber;

import javax.inject.Inject;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toSet;
import static java.util.stream.StreamSupport.stream;

public abstract class DefaultJvmTestSuite implements JvmTestSuite {

    public static final String JUNIT_PLATFORM_COORDINATES = "org.junit.platform:junit-platform-launcher";

    /**
     * Dependency information and default versions for supported testing frameworks.
     * When updating these versions, be sure to update the default versions noted in `JvmTestSuite` javadoc
     */
    //TODO This enum should just go away
    @VisibleForTesting
    public enum TestingFramework {
        JUNIT4("junit", "junit", "4.13.2");
//        SPOCK("org.spockframework", "spock-core", getAppropriateSpockVersion(), Collections.singletonList(
//                // spock-core references junit-jupiter's BOM, which in turn specifies the platform version
//                "org.junit.platform:junit-platform-launcher"
//        )),
//        KOTLIN_TEST("org.jetbrains.kotlin", "kotlin-test-junit5", "1.8.21", Collections.singletonList(
//                // kotlin-test-junit5 depends on junit-jupiter, which in turn specifies the platform version
//                "org.junit.platform:junit-platform-launcher"
//        )),

        private final String groupName;

        private final String defaultVersion;

        TestingFramework(String group, String name, String defaultVersion) {
            this.groupName = group + ":" + name;
            this.defaultVersion = defaultVersion;
        }

        public String getDefaultVersion() {
            return defaultVersion;
        }

        public List<String> getImplementationDependencies(String version) {
            return Collections.singletonList(groupName + ":" + version);
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

    private final ExtensiblePolymorphicDomainObjectContainer<JvmTestSuiteTarget> targets;
    private final SourceSet sourceSet;
    private final String name;
    private final JvmComponentDependencies dependencies;
    private final TaskDependencyFactory taskDependencyFactory;
    private final ProviderFactory providerFactory;
    private final TestEngineContainer testEngineContainer;

    @Inject
    public DefaultJvmTestSuite(String name, SourceSetContainer sourceSets, ConfigurationContainer configurations, TaskDependencyFactory taskDependencyFactory, ProviderFactory providerFactory) {
        this.name = name;
        this.sourceSet = sourceSets.create(getName());
        this.taskDependencyFactory = taskDependencyFactory;
        this.providerFactory = providerFactory;

        Configuration compileOnly = configurations.getByName(sourceSet.getCompileOnlyConfigurationName());
        Configuration implementation = configurations.getByName(sourceSet.getImplementationConfigurationName());
        Configuration runtimeOnly = configurations.getByName(sourceSet.getRuntimeOnlyConfigurationName());
        Configuration annotationProcessor = configurations.getByName(sourceSet.getAnnotationProcessorConfigurationName());

        this.dependencies = getObjectFactory().newInstance(
                DefaultJvmComponentDependencies.class,
                getObjectFactory().newInstance(DefaultDependencyAdder.class, implementation),
                getObjectFactory().newInstance(DefaultDependencyAdder.class, compileOnly),
                getObjectFactory().newInstance(DefaultDependencyAdder.class, runtimeOnly),
                getObjectFactory().newInstance(DefaultDependencyAdder.class, annotationProcessor)
        );

        this.testEngineContainer = getObjectFactory().newInstance(DefaultTestEngineContainer.class);

        this.targets = getObjectFactory().polymorphicDomainObjectContainer(JvmTestSuiteTarget.class);
        this.targets.registerFactory(JvmTestSuiteTarget.class, targetName -> {
            TestEngineParametersContainer engineParameters = getObjectFactory().newInstance(DefaultTestEngineParametersContainer.class, testEngineContainer);
            return getObjectFactory().newInstance(DefaultJvmTestSuiteTarget.class, targetName, engineParameters);
        });

        configureTestEngineOrFrameworkConventions(name, providerFactory, implementation);
        addDefaultTestTarget();
        configureTestSuiteTargetFrameworks(name);
    }

    private void configureTestSuiteTargetFrameworks(String name) {
        this.targets.withType(JvmTestSuiteTarget.class).configureEach(target -> {
            target.getTestTask().configure(task -> {
                initializeTargetTestFramework(name, task);
            });
        });
    }

    private void configureTestEngineOrFrameworkConventions(String name, ProviderFactory providerFactory, Configuration implementation) {
        if (!name.equals(JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME)) {
            // Conventionally use the Jupiter test engine for all test suites except the default one
            testEngineContainer.convention(engines -> engines.register(JUnitJupiterTestEngine.class));
            this.dependencies.getRuntimeOnly().add(JUNIT_PLATFORM_COORDINATES);
        } else {
            implementation.withDependencies(dependencySet -> {
                // Only add the platform launcher if test engines have been configured on the default test suite
                this.dependencies.getRuntimeOnly().bundle(providerFactory.provider(() ->
                        createDependencies(
                            ifTestEnginesEmpty(Collections.<String>emptyList())
                                .orElse(Collections.singletonList(JUNIT_PLATFORM_COORDINATES))
                        )
                ));
            });
        }

        // This is a workaround for strange behavior from the Kotlin plugin
        //
        // The Kotlin plugin attempts to look at the declared dependencies to know if it needs to add its own dependencies.
        // We avoid triggering realization of getTestSuiteTestingFramework by only adding our dependencies just before
        // resolution.
        bundleDependencies(this.dependencies.getImplementation(), TestEngine::getImplementationDependencies);
        bundleDependencies(this.dependencies.getCompileOnly(), TestEngine::getCompileOnlyDependencies);
        bundleDependencies(this.dependencies.getRuntimeOnly(), TestEngine::getRuntimeOnlyDependencies);
    }

    private void bundleDependencies(DependencyAdder dependencyAdder, Function<TestEngine<?>, Iterable<Dependency>> mapToDependencies) {
        dependencyAdder.bundle(providerFactory.provider(() ->
            ((TestEngineContainerInternal) testEngineContainer).getTestEngines().stream()
                .flatMap(engine -> stream(mapToDependencies.apply(engine).spliterator(), false))
                .collect(toSet())));
    }

    private void initializeTargetTestFramework(String name, Test task) {
        if (name.equals(JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME)) {
            // In order to maintain compatibility for the default test suite, we need to load JUnit4 from the Gradle distribution
            // instead of including it in testImplementation.
            TestFramework defaultJUnit4Framework = new JUnitTestFramework(task, (DefaultTestFilter) task.getFilter(), true);
            TestFramework junitPlatformFramework = new JUnitPlatformTestFramework((DefaultTestFilter) task.getFilter(), false, task.getDryRun());
            // Use JUnit4 for the default test suite if no test engines are configured, otherwise use JUnit platform
            Provider<TestFramework> junit4UnlessTestEnginesAreConfigured = providerFactory.provider(() ->
                ifTestEnginesEmpty(defaultJUnit4Framework).orElse(junitPlatformFramework)
            );
            task.getTestFrameworkProperty().convention(junit4UnlessTestEnginesAreConfigured);
        } else {
            // Use JUnit platform for any test suite other than the default test suite
            task.getTestFrameworkProperty().convention(new JUnitPlatformTestFramework((DefaultTestFilter) task.getFilter(), false, task.getDryRun()));
        }
    }

    private <T> Optional<T> ifTestEnginesEmpty(T ifEmpty) {
        return Optional.ofNullable(((TestEngineContainerInternal) testEngineContainer).isEmpty() ? ifEmpty : null);
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

    private List<ExternalModuleDependency> createDependencies(List<String> dependencies) {
        return dependencies.stream().map(getDependencyFactory()::create).collect(Collectors.toList());
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

    @Override
    public TestEngineContainer getTestEngines() {
        return testEngineContainer;
    }
}
