/*
 * Copyright 2023 the original author or authors.
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

import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.ConfigurationContainer;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.plugins.internal.JavaConfigurationVariantMapping;
import org.gradle.api.plugins.internal.JavaPluginHelper;
import org.gradle.api.plugins.jvm.internal.DefaultJvmFeature;
import org.gradle.api.plugins.jvm.internal.JvmFeatureInternal;
import org.gradle.api.tasks.SourceSet;
import org.gradle.internal.component.external.model.ProjectDerivedCapability;
import org.gradle.jvm.component.internal.DefaultJvmSoftwareComponent;

import javax.inject.Inject;
import java.util.Collections;

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
 * This should really be named `JVMTestFixturesPlugin`, as there is no requirement
 * for the test fixtures to be written in Java, any supported JVM language will work.
 *
 * @since 5.6
 * @see <a href="https://docs.gradle.org/current/userguide/java_testing.html#sec:java_test_fixtures">Java Test Fixtures reference</a>
 */
public abstract class JavaTestFixturesPlugin implements Plugin<Project> {

    @Inject
    public JavaTestFixturesPlugin() { }

    @Override
    public void apply(Project project) {

        project.getPlugins().apply(JavaBasePlugin.class);
        JavaPluginExtension extension = project.getExtensions().getByType(JavaPluginExtension.class);

        SourceSet testFixturesSourceSet = extension.getSourceSets().maybeCreate(TEST_FIXTURES_FEATURE_NAME);

        JvmFeatureInternal feature = new DefaultJvmFeature(
            TEST_FIXTURES_FEATURE_NAME,
            testFixturesSourceSet,
            Collections.singleton(new ProjectDerivedCapability(project, TEST_FIXTURES_FEATURE_NAME)),
            (ProjectInternal) project,
            false
        );

        feature.withApi();

        project.getPluginManager().withPlugin("java", plugin -> {
            DefaultJvmSoftwareComponent component = (DefaultJvmSoftwareComponent) JavaPluginHelper.getJavaComponent(project);
            component.getFeatures().add(feature);

            component.addVariantsFromConfiguration(feature.getApiElementsConfiguration(), new JavaConfigurationVariantMapping("compile", true, feature.getCompileClasspathConfiguration()));
            component.addVariantsFromConfiguration(feature.getRuntimeElementsConfiguration(), new JavaConfigurationVariantMapping("runtime", true, feature.getRuntimeClasspathConfiguration()));

            createImplicitTestFixturesDependencies(feature, project);
        });
    }

    private void createImplicitTestFixturesDependencies(JvmFeatureInternal feature, Project project) {
        DependencyHandler dependencies = project.getDependencies();

        // Test fixtures depend on the project.
        feature.getApiConfiguration().getDependencies().add(dependencies.create(project));

        // The tests depend on the test fixtures.
        SourceSet testSourceSet = JavaPluginHelper.getDefaultTestSuite(project).getSources();
        Configuration testImplementation = project.getConfigurations().getByName(testSourceSet.getImplementationConfigurationName());
        testImplementation.getDependencies().add(dependencies.testFixtures(dependencies.create(project)));

        // Overwrite what the Java plugin defines for test, in order to avoid duplicate classes
        // see gradle/gradle#10872
        ConfigurationContainer configurations = project.getConfigurations();
        testSourceSet.setCompileClasspath(project.getObjects().fileCollection().from(configurations.getByName(testSourceSet.getCompileClasspathConfigurationName())));
        testSourceSet.setRuntimeClasspath(project.getObjects().fileCollection().from(testSourceSet.getOutput(), configurations.getByName(testSourceSet.getRuntimeClasspathConfigurationName())));
    }

}
