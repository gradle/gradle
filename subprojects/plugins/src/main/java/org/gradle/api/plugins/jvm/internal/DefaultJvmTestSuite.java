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
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.TestSuitePlugin;
import org.gradle.api.plugins.jvm.ComponentDependencies;
import org.gradle.api.plugins.jvm.JunitPlatformTestingFramework;
import org.gradle.api.plugins.jvm.JunitTestingFramework;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.JvmTestSuiteTarget;
import org.gradle.api.plugins.jvm.JvmTestingFramework;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.SourceSetOutput;
import org.gradle.api.tasks.TaskContainer;

import javax.inject.Inject;
import java.util.concurrent.Callable;

public abstract class DefaultJvmTestSuite implements JvmTestSuite {
    private final ExtensiblePolymorphicDomainObjectContainer<JvmTestSuiteTarget> targets;
    private final SourceSet sourceSet;
    private final String name;
    private final ComponentDependencies dependencies;

    @Inject
    public DefaultJvmTestSuite(String name, SourceSetContainer sourceSets, ConfigurationContainer configurations, TaskContainer tasks, DependencyHandler dependencies) {
        this.name = name;

        if (getName().equals(TestSuitePlugin.DEFAULT_TEST_SUITE_NAME)) {
            this.sourceSet = sourceSets.create(SourceSet.TEST_SOURCE_SET_NAME);
        } else {
            this.sourceSet = sourceSets.create(getName());
        }

        Configuration compileOnly = configurations.getByName(sourceSet.getCompileOnlyConfigurationName());
        Configuration implementation = configurations.getByName(sourceSet.getImplementationConfigurationName());
        Configuration runtimeOnly = configurations.getByName(sourceSet.getRuntimeOnlyConfigurationName());

        if (getName().equals(TestSuitePlugin.DEFAULT_TEST_SUITE_NAME)) {
            dependencies.add(implementation.getName(), getObjectFactory().fileCollection().from( (Callable<SourceSetOutput>) () -> sourceSets.getByName("main").getOutput()));
        }

        dependencies.addProvider(implementation.getName(), getTestingFramework().map(framework -> {
            if (framework instanceof JunitPlatformTestingFramework) {
                return "org.junit.jupiter:junit-jupiter-api:" + framework.getVersion().get();
            } else {
                return "junit:junit:" + framework.getVersion().get();
            }
        }));
        dependencies.addProvider(runtimeOnly.getName(), getTestingFramework().map(framework -> {
            if (framework instanceof JunitPlatformTestingFramework) {
                return "org.junit.jupiter:junit-jupiter-engine:" + framework.getVersion().get();
            } else {
                return getObjectFactory().fileCollection();
            }
        }));


//        test.setCompileClasspath(project.getObjects().fileCollection().from(main.getOutput(), project.getConfigurations().getByName(TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME)));
//        test.setRuntimeClasspath(project.getObjects().fileCollection().from(test.getOutput(), main.getOutput(), project.getConfigurations().getByName(TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME)));

        this.targets = getObjectFactory().polymorphicDomainObjectContainer(JvmTestSuiteTarget.class);
        targets.registerFactory(JvmTestSuiteTarget.class, targetName -> getObjectFactory().newInstance(DefaultJvmTestSuiteTarget.class, targetName, tasks));

        this.dependencies = getObjectFactory().newInstance(DefaultComponentDependencies.class, implementation, compileOnly, runtimeOnly);
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

    public void addTestTarget(JavaPluginExtension java) {
        final String target;
        if (getName().equals(TestSuitePlugin.DEFAULT_TEST_SUITE_NAME)) {
            target = JavaPlugin.TEST_TASK_NAME;
        } else {
            target = getName() + "java" + java.getSourceCompatibility().getMajorVersion();
        }

        DefaultJvmTestSuiteTarget defaultJvmTestSuiteTarget = getObjectFactory().newInstance(DefaultJvmTestSuiteTarget.class, target);

        defaultJvmTestSuiteTarget.getJavaVersion().set(java.getSourceCompatibility());
        defaultJvmTestSuiteTarget.getJavaVersion().finalizeValue();

        targets.add(defaultJvmTestSuiteTarget);
    }

    @Override
    public void useJunit() {
        JvmTestingFramework testingFramework = getObjectFactory().newInstance(JunitTestingFramework.class);
        getTestingFramework().convention(testingFramework);
        testingFramework.getVersion().convention("4.13");
    }

    @Override
    public void useJunitPlatform() {
        JvmTestingFramework testingFramework = getObjectFactory().newInstance(JunitPlatformTestingFramework.class);
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
}
