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
import org.gradle.api.Action;
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.Dependency;
import org.gradle.api.artifacts.dsl.DependencyFactory;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyAdder;
import org.gradle.api.internal.provider.DefaultProvider;
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

import javax.annotation.Nullable;
import javax.inject.Inject;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class DefaultJvmTestSuite implements JvmTestSuite {

    private static class Module {
        private final String group;
        private final String name;
        private final String version;

        public Module(String group, String name, @Nullable String version) {
            this.group = group;
            this.name = name;
            this.version = version;
        }

        public String getDefaultVersionNotation() {
            return getNotationForVersion(version);
        }

        public String getNotationForVersion(@Nullable String specified) {
            return group + ":" + name + (specified != null ?  ":" + specified : "");
        }
    }

    // Should be the same as the version listed in the junit-bom corresponding to the default junit-jupiter version.
    // Only used here for testing, but defined in this class to keep it near the default junit-jupiter version.
    @VisibleForTesting
    public static final String JUNIT_PLATFORM_DEFAULT_VERSION = "1.8.2";

    @VisibleForTesting
    public enum TestingFramework {
        // When updating these versions, be sure to update the default versions noted in `JvmTestSuite` javadoc
        JUNIT4("junit", "junit", "4.13.2"),
        JUNIT_JUPITER("org.junit.jupiter", "junit-jupiter", "5.8.2", Collections.singletonList(
            // junit-jupiter's BOM, junit-bom, specifies the platform version
            new Module("org.junit.platform", "junit-platform-launcher", null)
        )),
        SPOCK("org.spockframework", "spock-core", "2.1-groovy-3.0"),
        KOTLIN_TEST("org.jetbrains.kotlin", "kotlin-test-junit5", "1.7.10", Collections.singletonList(
            // kotlin-test-junit5 depends on junit-jupiter, which in turn specifies the platform version
            new Module("org.junit.platform", "junit-platform-launcher", null)
        )),
        TESTNG("org.testng", "testng", "7.5");

        private final Module module;
        private final List<Module> dependencies;

        TestingFramework(String group, String name, String defaultVersion) {
            this(group, name, defaultVersion, Collections.emptyList());
        }

        TestingFramework(String group, String name, String defaultVersion, List<Module> dependencies) {
            this.module = new Module(group, name, defaultVersion);
            this.dependencies = dependencies;
        }

        public String getDefaultVersion() {
            return module.version;
        }

        public List<Dependency> getDependencies(String version, DependencyFactory dependencyFactory) {
            List<Dependency> deps = new ArrayList<>(1 + dependencies.size());
            deps.add(dependencyFactory.create(module.getNotationForVersion(version)));
            for (Module dep : dependencies) {
                // TODO: Should we separate these between implementation and runtime dependencies?
                // Do we need junit-platform-launcher on the compile classpath or only during runtime?
                deps.add(dependencyFactory.create(dep.getDefaultVersionNotation()));
            }
            return deps;
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

        public List<Dependency> getDependencies(DependencyFactory dependencyFactory) {
            return framework.getDependencies(version, dependencyFactory);
        }
    }

    private final ExtensiblePolymorphicDomainObjectContainer<JvmTestSuiteTarget> targets;
    private final SourceSet sourceSet;
    private final String name;
    private final DependencyFactory dependencyFactory;
    private final JvmComponentDependencies dependencies;
    private boolean attachedDependencies = false;

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
        } else {
            // for the built-in test suite, we don't express an opinion, so we will not add any dependencies
            // if a user explicitly calls useJUnit or useJUnitJupiter, the built-in test suite will behave like a custom one
            // and add dependencies automatically.
            getVersionedTestingFramework().convention((VersionedTestingFramework) null);
        }

        addDefaultTestTarget();

        // Until the values here can be finalized upon the user setting them (see the org.gradle.api.tasks.testing.Test#testFramework(Closure) method),
        // in Gradle 8, we will be executing the provider lambda used as the convention multiple times.  So make sure, within a Test Suite, that we
        // always return the same one via computeIfAbsent() against this map.
        final Map<TestingFramework, TestFramework> frameworkLookup = new HashMap<>(4);

        this.targets.withType(JvmTestSuiteTarget.class).configureEach(target -> {
            target.getTestTask().configure(task -> {
                task.getTestFrameworkProperty().convention(getVersionedTestingFramework().map(vtf -> {
                    switch(vtf.getFramework()) {
                        case JUNIT4:
                            return frameworkLookup.computeIfAbsent(vtf.getFramework(), f -> new JUnitTestFramework(task, (DefaultTestFilter) task.getFilter(), false));
                        case KOTLIN_TEST: // fall-through
                        case JUNIT_JUPITER: // fall-through
                        case SPOCK:
                            return frameworkLookup.computeIfAbsent(vtf.getFramework(), f -> new JUnitPlatformTestFramework((DefaultTestFilter) task.getFilter(), false));
                        case TESTNG:
                            return frameworkLookup.computeIfAbsent(vtf.getFramework(), f -> new TestNGTestFramework(task, task.getClasspath(), (DefaultTestFilter) task.getFilter(), getObjectFactory()));
                        default:
                            throw new IllegalStateException("do not know how to handle " + vtf);
                    }
                    // In order to maintain compatibility for the default test suite, we need to load JUnit4 from the Gradle distribution
                    // instead of including it in testImplementation.
                    // TODO: In 8.0 should we switch getVersionedTestingFramework() to use JUNIT4 as a convention?
                }).orElse(new DefaultProvider<>(() -> frameworkLookup.computeIfAbsent(null, f -> new JUnitTestFramework(task, (DefaultTestFilter) task.getFilter(), true)))));
            });
        });
    }

    private void setFrameworkTo(TestingFramework framework, Provider<String> version) {
        getVersionedTestingFramework().set(version.map(v -> new VersionedTestingFramework(framework, v)));

        // This whole way of adding the implementation dependencies here is messed up. Once a user calls the useXXX method,
        // they can't go back. Maybe that's no so terrible, except that we call useJunitJupiter() FOR ALL USER DEFINED TEST SUITES.
        // This is a real problem. Users who want to use anything but JUnit Jupiter will always have the JUnit Jupiter
        // on their classpath and will have to manually declare the implementation dependencies for the test framework
        // they want to use.

        // There is a nice-seeming solution: We could move the line below, `.getImplementation.bundle(...)`, into the constructor.
        // This could seemingly work, except for if the configuration's dependencies ever get resolved early, before the user is
        // able to call their useXXX method. If that happens, the provider gets resolved early, and the dependencies defined at
        // that point are added to the configuration. Then, after the user calls their useXXX method, the provider is updated
        // but the configuration never is. This is a problem with how we declare dependencies on configurations. We should be
        // able to restrict users from resolving the configuration dependencies early OR make these dependencies even more lazy
        // so that if dependencies are resolved early, the future changes in the lazy properties are still propagated to later resolutions.

        // A concrete example of this occurs when using the Kotlin Gradle Plugin. See the function `configureKotlinTestDependency` added
        // at the below linked commit. This resolves the dependencies before configuration-time is over, and would break an implementation
        // where the below line is instead located in the constructor.
        // See: https://github.com/JetBrains/kotlin/commit/4a172286217a1a7d4e7a7f0eb6a0bc53ebf56515
        if (!attachedDependencies) {
            this.dependencies.getImplementation().bundle(getVersionedTestingFramework().map(vtf -> vtf.getDependencies(dependencyFactory)));
            attachedDependencies = true;
        }
    }

    @Override
    public String getName() {
        return name;
    }

    @Inject
    public abstract ObjectFactory getObjectFactory();

    @Inject
    public abstract ProviderFactory getProviderFactory();

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
}
