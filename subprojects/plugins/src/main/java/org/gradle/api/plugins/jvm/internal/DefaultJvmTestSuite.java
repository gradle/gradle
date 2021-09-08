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
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.tasks.AbstractTaskDependency;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.JvmTestSuitePlugin;
import org.gradle.api.plugins.jvm.ComponentDependencies;
import org.gradle.api.plugins.jvm.JunitPlatformTestingFramework;
import org.gradle.api.plugins.jvm.JunitTestingFramework;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.JvmTestSuiteTarget;
import org.gradle.api.plugins.jvm.JvmTestingFramework;
import org.gradle.api.provider.ProviderFactory;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.TaskDependency;

import javax.inject.Inject;

import java.util.concurrent.Callable;

import static org.gradle.api.plugins.JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME;
import static org.gradle.api.plugins.JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME;

public abstract class DefaultJvmTestSuite implements JvmTestSuite {
    private final ExtensiblePolymorphicDomainObjectContainer<JvmTestSuiteTarget> targets;
    private final SourceSet sourceSet;
    private final String name;
    private final ComponentDependencies dependencies;

    @Inject
    public DefaultJvmTestSuite(String name, Project project, JavaPluginExtension java) {
        this.name = name;

        if (getName().equals(JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME)) {
            this.sourceSet = java.getSourceSets().create(SourceSet.TEST_SOURCE_SET_NAME);
        } else {
            this.sourceSet = java.getSourceSets().create(getName());
        }

        Configuration compileOnly = project.getConfigurations().getByName(sourceSet.getCompileOnlyConfigurationName());
        Configuration implementation = project.getConfigurations().getByName(sourceSet.getImplementationConfigurationName());
        Configuration runtimeOnly = project.getConfigurations().getByName(sourceSet.getRuntimeOnlyConfigurationName());

        if (!getName().equals(JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME)) {
            project.getDependencies().addProvider(implementation.getName(), getTestingFramework().map(framework -> {
                if (framework instanceof JunitPlatformTestingFramework) {
                    return "org.junit.jupiter:junit-jupiter-api:" + framework.getVersion().get();
                } else {
                    return "junit:junit:" + framework.getVersion().get();
                }
            }));
            project.getDependencies().addProvider(runtimeOnly.getName(), getTestingFramework().map(framework -> {
                if (framework instanceof JunitPlatformTestingFramework) {
                    return "org.junit.jupiter:junit-jupiter-engine:" + framework.getVersion().get();
                } else {
                    return getObjectFactory().fileCollection();
                }
            }));
        } else {
            final Callable<FileCollection> mainSourceSetOutput = () -> java.getSourceSets().getByName(SourceSet.MAIN_SOURCE_SET_NAME).getOutput();
            final Callable<FileCollection> testSourceSetOutput = () -> java.getSourceSets().getByName(SourceSet.TEST_SOURCE_SET_NAME).getOutput();

            this.sourceSet.setCompileClasspath(project.getObjects().fileCollection().from(mainSourceSetOutput, project.getConfigurations().getByName(TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME)));
            this.sourceSet.setRuntimeClasspath(project.getObjects().fileCollection().from(testSourceSetOutput, mainSourceSetOutput, project.getConfigurations().getByName(TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME)));
        }

        this.targets = getObjectFactory().polymorphicDomainObjectContainer(JvmTestSuiteTarget.class);
        this.targets.registerFactory(JvmTestSuiteTarget.class, targetName -> getObjectFactory().newInstance(DefaultJvmTestSuiteTarget.class, this, targetName, project.getTasks()));

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
        if (getName().equals(JvmTestSuitePlugin.DEFAULT_TEST_SUITE_NAME)) {
            target = JavaPlugin.TEST_TASK_NAME;
        } else {
            target = getName(); // For now, we'll just name the test task for the single target for the suite with the suite name
        }

        DefaultJvmTestSuiteTarget defaultJvmTestSuiteTarget = getObjectFactory().newInstance(DefaultJvmTestSuiteTarget.class, this, target);

        defaultJvmTestSuiteTarget.getJavaVersion().set(java.getSourceCompatibility());
        defaultJvmTestSuiteTarget.getJavaVersion().finalizeValue();

        targets.add(defaultJvmTestSuiteTarget);
    }

    @Override
    public void useJUnit() {
        JvmTestingFramework testingFramework = getObjectFactory().newInstance(JunitTestingFramework.class);
        getTestingFramework().convention(testingFramework);
        testingFramework.getVersion().convention("4.13");
    }

    @Override
    public void useJUnitPlatform() {
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
