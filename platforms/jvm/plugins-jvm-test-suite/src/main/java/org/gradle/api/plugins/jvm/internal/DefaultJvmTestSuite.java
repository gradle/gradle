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

import com.google.common.collect.Maps;
import org.gradle.api.Action;
import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.internal.artifacts.dsl.dependencies.DefaultDependencyAdder;
import org.gradle.api.internal.tasks.JvmConstants;
import org.gradle.api.internal.tasks.TaskDependencyFactory;
import org.gradle.api.plugins.internal.JvmTestSuitePluginHelper;
import org.gradle.api.testing.toolchains.internal.FrameworkCachingJvmTestToolchain;
import org.gradle.api.testing.toolchains.internal.JUnit4TestToolchain;
import org.gradle.api.testing.toolchains.internal.JUnit4ToolchainParameters;
import org.gradle.api.testing.toolchains.internal.JUnitJupiterTestToolchain;
import org.gradle.api.testing.toolchains.internal.JUnitJupiterToolchainParameters;
import org.gradle.api.testing.toolchains.internal.JvmTestToolchain;
import org.gradle.api.testing.toolchains.internal.JvmTestToolchainParameters;
import org.gradle.api.testing.toolchains.internal.KotlinTestTestToolchain;
import org.gradle.api.testing.toolchains.internal.KotlinTestToolchainParameters;
import org.gradle.api.testing.toolchains.internal.LegacyJUnit4TestToolchain;
import org.gradle.api.testing.toolchains.internal.SpockTestToolchain;
import org.gradle.api.testing.toolchains.internal.SpockToolchainParameters;
import org.gradle.api.testing.toolchains.internal.TestNGTestToolchain;
import org.gradle.api.testing.toolchains.internal.TestNGToolchainParameters;
import org.gradle.api.model.ObjectFactory;
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
import org.gradle.internal.Actions;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.isolated.IsolationScheme;
import org.gradle.internal.service.ServiceLookup;
import org.gradle.internal.service.ServiceRegistry;

import javax.inject.Inject;
import java.util.Collections;
import java.util.Map;

import static org.gradle.internal.Cast.uncheckedCast;

public abstract class DefaultJvmTestSuite implements JvmTestSuite {
    private final ExtensiblePolymorphicDomainObjectContainer<JvmTestSuiteTarget> targets;
    private final SourceSet sourceSet;
    private final String name;
    private final JvmComponentDependencies dependencies;
    private final TaskDependencyFactory taskDependencyFactory;
    private final ToolchainFactory toolchainFactory;

    @Inject
    public DefaultJvmTestSuite(String name, SourceSetContainer sourceSets, ConfigurationContainer configurations, TaskDependencyFactory taskDependencyFactory) {
        this.name = name;
        this.sourceSet = sourceSets.create(getName());
        this.taskDependencyFactory = taskDependencyFactory;
        this.toolchainFactory = new ToolchainFactory(getObjectFactory(), getParentServices(), getInstantiatorFactory());

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

        if (name.equals(JvmTestSuitePluginHelper.DEFAULT_TEST_SUITE_NAME)) {
            // for the built-in test suite, we don't express an opinion, so we will not add any dependencies
            // if a user explicitly calls useJUnit or useJUnitJupiter, the built-in test suite will behave like a custom one
            // and add dependencies automatically.
            getTestToolchain().convention(toolchainFactory.getOrCreate(LegacyJUnit4TestToolchain.class));
        } else {
            JvmTestToolchain<? extends JUnitJupiterToolchainParameters> toolchain = toolchainFactory.getOrCreate(JUnitJupiterTestToolchain.class);
            getTestToolchain().convention(toolchain);
            toolchain.getParameters().getJupiterVersion().convention(JUnitJupiterTestToolchain.DEFAULT_VERSION);
        }

        addDefaultTestTarget();

        this.targets.withType(JvmTestSuiteTarget.class).configureEach(target -> {
            target.getTestTask().configure(this::initializeTestFramework);
        });

        // This is a workaround for strange behavior from the Kotlin plugin
        //
        // The Kotlin plugin attempts to look at the declared dependencies to know if it needs to add its own dependencies.
        // We avoid triggering realization of getTestSuiteTestingFramework by only adding our dependencies just before
        // resolution.
        implementation.withDependencies(dependencySet ->
            dependencySet.addAllLater(getTestToolchain().map(JvmTestToolchain::getImplementationDependencies)
                .orElse(Collections.emptyList()))
        );
        runtimeOnly.withDependencies(dependencySet ->
            dependencySet.addAllLater(getTestToolchain().map(JvmTestToolchain::getRuntimeOnlyDependencies)
                .orElse(Collections.emptyList()))
        );
        compileOnly.withDependencies(dependenciesSet ->
            dependenciesSet.addAllLater(getTestToolchain().map(JvmTestToolchain::getCompileOnlyDependencies)
                .orElse(Collections.emptyList()))
        );
    }

    private void initializeTestFramework(Test task) {
        // The Test task's testing framework is derived from the test suite's toolchain
        task.getTestFrameworkProperty().convention(getTestToolchain().map(toolchain -> toolchain.createTestFramework(task)));
    }

    private void addDefaultTestTarget() {
        final String target;
        if (getName().equals(JvmTestSuitePluginHelper.DEFAULT_TEST_SUITE_NAME)) {
            target = JvmConstants.TEST_TASK_NAME;
        } else {
            target = getName(); // For now, we'll just name the test task for the single target for the suite with the suite name
        }

        targets.register(target);
    }

    protected abstract Property<JvmTestToolchain<?>> getTestToolchain();

    @Override
    public String getName() {
        return name;
    }

    @Inject
    public abstract ObjectFactory getObjectFactory();

    @Inject
    public abstract ProviderFactory getProviderFactory();

    @Inject
    public abstract InstantiatorFactory getInstantiatorFactory();

    @Inject
    public abstract ServiceRegistry getParentServices();

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
        useJUnit(Actions.doNothing());
    }

    @Override
    public void useJUnit(String version) {
        useJUnit(getProviderFactory().provider(() -> version));
    }

    @Override
    public void useJUnit(Provider<String> version) {
        useJUnit(parameters -> parameters.getVersion().set(version));
    }

    private void useJUnit(Action<JUnit4ToolchainParameters> action) {
        setToolchainAndConfigure(JUnit4TestToolchain.class, parameters -> {
            parameters.getVersion().convention(JUnit4TestToolchain.DEFAULT_VERSION);
            action.execute(parameters);
        });
    }

    @Override
    public void useJUnitJupiter() {
        useJUnitJupiter(Actions.doNothing());
    }

    @Override
    public void useJUnitJupiter(String version) {
        useJUnitJupiter(getProviderFactory().provider(() -> version));
    }

    @Override
    public void useJUnitJupiter(Provider<String> version) {
        useJUnitJupiter(parameters -> parameters.getJupiterVersion().set(version));
    }

    private void useJUnitJupiter(Action<JUnitJupiterToolchainParameters> action) {
        setToolchainAndConfigure(JUnitJupiterTestToolchain.class, parameters -> {
            parameters.getJupiterVersion().convention(JUnitJupiterTestToolchain.DEFAULT_VERSION);
            action.execute(parameters);
        });
    }

    @Override
    public void useSpock() {
        useSpock(Actions.doNothing());
    }

    @Override
    public void useSpock(String version) {
        useSpock(getProviderFactory().provider(() -> version));
    }

    @Override
    public void useSpock(Provider<String> version) {
        useSpock(parameters -> parameters.getSpockVersion().set(version));
    }

    private void useSpock(Action<SpockToolchainParameters> action) {
        setToolchainAndConfigure(SpockTestToolchain.class, parameters -> {
            parameters.getSpockVersion().convention(SpockTestToolchain.DEFAULT_VERSION);
            action.execute(parameters);
        });
    }

    @Override
    public void useKotlinTest() {
        useKotlinTest(Actions.doNothing());
    }

    @Override
    public void useKotlinTest(String version) {
        useKotlinTest(getProviderFactory().provider(() -> version));
    }

    @Override
    public void useKotlinTest(Provider<String> version) {
        useKotlinTest(parameters -> parameters.getKotlinTestVersion().set(version));
    }

    private void useKotlinTest(Action<KotlinTestToolchainParameters> action) {
        setToolchainAndConfigure(KotlinTestTestToolchain.class, parameters -> {
            parameters.getKotlinTestVersion().convention(KotlinTestTestToolchain.DEFAULT_VERSION);
            action.execute(parameters);
        });
    }

    @Override
    public void useTestNG() {
        useTestNG(Actions.doNothing());
    }

    @Override
    public void useTestNG(String version) {
        useTestNG(getProviderFactory().provider(() -> version));
    }

    @Override
    public void useTestNG(Provider<String> version) {
        useTestNG(parameters -> parameters.getVersion().set(version));
    }

    private void useTestNG(Action<TestNGToolchainParameters> action) {
        setToolchainAndConfigure(TestNGTestToolchain.class, parameters -> {
            parameters.getVersion().convention(TestNGTestToolchain.DEFAULT_VERSION);
            action.execute(parameters);
        });
    }

    private <T extends JvmTestToolchainParameters> void setToolchainAndConfigure(Class<? extends JvmTestToolchain<T>> toolchainType, Action<T> action) {
        JvmTestToolchain<T> toolchain = toolchainFactory.getOrCreate(toolchainType);
        getTestToolchain().set(toolchain);
        action.execute(toolchain.getParameters());
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

    /**
     * Creates and caches toolchains.  This allows multiple calls to the same use* methods to operate on the same toolchain and maintain the same
     * test framework options instances.
     */
    private static class ToolchainFactory {
        private final ObjectFactory objectFactory;
        private final ServiceLookup parentServices;
        private final InstantiatorFactory instantiatorFactory;
        private final Map<Class<? extends JvmTestToolchain<?>>, JvmTestToolchain<?>> cache = Maps.newHashMap();

        public ToolchainFactory(ObjectFactory objectFactory, ServiceLookup parentServices, InstantiatorFactory instantiatorFactory) {
            this.objectFactory = objectFactory;
            this.parentServices = parentServices;
            this.instantiatorFactory = instantiatorFactory;
        }

        public <T extends JvmTestToolchain<?>> T getOrCreate(Class<T> type) {
            return uncheckedCast(cache.computeIfAbsent(type, t -> create(uncheckedCast(type))));
        }

        private <T extends JvmTestToolchainParameters> JvmTestToolchain<T> create(Class<? extends JvmTestToolchain<T>> type) {
            IsolationScheme<JvmTestToolchain<?>, JvmTestToolchainParameters> isolationScheme = new IsolationScheme<>(uncheckedCast(JvmTestToolchain.class), JvmTestToolchainParameters.class, JvmTestToolchainParameters.None.class);
            Class<T> parametersType = isolationScheme.parameterTypeFor(type);
            T parameters = parametersType == null ? null : objectFactory.newInstance(parametersType);
            ServiceLookup lookup = isolationScheme.servicesForImplementation(parameters, parentServices, Collections.emptyList(), p -> true);
            return new FrameworkCachingJvmTestToolchain<>(instantiatorFactory.decorate(lookup).newInstance(type));
        }
    }
}
