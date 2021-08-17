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
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.plugins.JavaPlugin;
import org.gradle.api.plugins.JavaPluginExtension;
import org.gradle.api.plugins.TestSuitePlugin;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.JvmTestSuiteTarget;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.TaskContainer;

import javax.inject.Inject;

public abstract class DefaultJvmTestSuite implements JvmTestSuite {
    private final ExtensiblePolymorphicDomainObjectContainer<JvmTestSuiteTarget> targets;
    private final SourceSet sourceSet;
    private final String name;

    @Inject
    public DefaultJvmTestSuite(String name, SourceSetContainer sourceSets, ConfigurationContainer configurations, TaskContainer tasks) {
        this.name = name;
        this.sourceSet = sourceSets.create(getName());
        Configuration compileOnly = configurations.getByName(sourceSet.getCompileOnlyConfigurationName());
        Configuration implementation = configurations.getByName(sourceSet.getImplementationConfigurationName());
        Configuration runtimeOnly = configurations.getByName(sourceSet.getRuntimeOnlyConfigurationName());
        this.targets = getObjectFactory().polymorphicDomainObjectContainer(JvmTestSuiteTarget.class);

        targets.registerFactory(JvmTestSuiteTarget.class, targetName -> getObjectFactory().newInstance(DefaultJvmTestSuiteTarget.class, targetName, tasks));
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

    public void addTestTarget(JavaPluginExtension java) {
        final String target;
        if (getName().equals(TestSuitePlugin.DEFAULT_TEST_SUITE_NAME)) {
            target = JavaPlugin.TEST_TASK_NAME;
        } else {
            target = getName() + "java" + java.getSourceCompatibility().getMajorVersion();
        }

        DefaultJvmTestSuiteTarget defaultJvmTestSuiteTarget = getObjectFactory().newInstance(DefaultJvmTestSuiteTarget.class, target);
        defaultJvmTestSuiteTarget.getTestClasses().from(sourceSet.getOutput().getClassesDirs());
        defaultJvmTestSuiteTarget.getRuntimeClasspath().from(sourceSet.getRuntimeClasspath());

        defaultJvmTestSuiteTarget.getJavaVersion().set(java.getSourceCompatibility());
        defaultJvmTestSuiteTarget.getJavaVersion().finalizeValue();

        targets.add(defaultJvmTestSuiteTarget);
    }
}
