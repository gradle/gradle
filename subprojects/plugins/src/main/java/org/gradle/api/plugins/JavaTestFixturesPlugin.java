/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.api.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.ProjectDependency;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.plugins.jvm.internal.JvmModelingServices;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.component.external.model.ProjectTestFixtures;

import javax.inject.Inject;

import static org.gradle.api.plugins.JavaPlugin.TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME;
import static org.gradle.api.plugins.JavaPlugin.TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME;
import static org.gradle.internal.component.external.model.TestFixturesSupport.TEST_FIXTURES_API;
import static org.gradle.internal.component.external.model.TestFixturesSupport.TEST_FIXTURES_FEATURE_NAME;

/**
 * Adds support for producing test fixtures. This plugin will automatically
 * create a `testFixtures` source set, and wires the tests to use those
 * test fixtures automatically.
 *
 * Other projects may consume the test fixtures of the current project by
 * declaring a dependency using the {@link DependencyHandler#testFixtures(Object)}
 * method.
 *
 * @since 5.6
 */
@Incubating
public class JavaTestFixturesPlugin implements Plugin<Project> {

    private final JvmModelingServices jvmEcosystemUtilities;

    @Inject
    public JavaTestFixturesPlugin(JvmModelingServices jvmModelingServices) {
        this.jvmEcosystemUtilities = jvmModelingServices;
    }

    @Override
    public void apply(Project project) {
        project.getPluginManager().withPlugin("java", plugin -> {
            jvmEcosystemUtilities.createJvmVariant(TEST_FIXTURES_FEATURE_NAME, builder ->
                builder
                    .exposesApi()
                    .published()
            );
            createImplicitTestFixturesDependencies(project, findJavaConvention(project));
        });
    }

    private void createImplicitTestFixturesDependencies(Project project, JavaPluginConvention convention) {
        DependencyHandler dependencies = project.getDependencies();
        dependencies.add(TEST_FIXTURES_API, dependencies.create(project));
        SourceSet testSourceSet = findTestSourceSet(convention);
        ProjectDependency testDependency = (ProjectDependency) dependencies.add(testSourceSet.getImplementationConfigurationName(), dependencies.create(project));
        testDependency.capabilities(new ProjectTestFixtures(project));

        // Overwrite what the Java plugin defines for test, in order to avoid duplicate classes
        // see gradle/gradle#10872
        ConfigurationContainer configurations = project.getConfigurations();
        testSourceSet.setCompileClasspath(project.getObjects().fileCollection().from(configurations.getByName(TEST_COMPILE_CLASSPATH_CONFIGURATION_NAME)));
        testSourceSet.setRuntimeClasspath(project.getObjects().fileCollection().from(testSourceSet.getOutput(), configurations.getByName(TEST_RUNTIME_CLASSPATH_CONFIGURATION_NAME)));

    }

    private SourceSet findTestSourceSet(JavaPluginConvention convention) {
        return convention.getSourceSets().getByName("test");
    }

    private JavaPluginConvention findJavaConvention(Project project) {
        return (JavaPluginConvention) project.getConvention().getPlugins().get("java");
    }

}
