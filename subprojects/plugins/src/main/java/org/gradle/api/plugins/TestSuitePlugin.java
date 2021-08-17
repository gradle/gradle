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

package org.gradle.api.plugins;

import org.gradle.api.ExtensiblePolymorphicDomainObjectContainer;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.plugins.internal.DefaultTestingExtension;
import org.gradle.api.plugins.jvm.JvmTestSuite;
import org.gradle.api.plugins.jvm.internal.DefaultJvmTestSuite;

public class TestSuitePlugin  implements Plugin<Project> {
    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("org.gradle.java-base");

        ExtensiblePolymorphicDomainObjectContainer<JvmTestSuite> testSuites = project.getObjects().polymorphicDomainObjectContainer(JvmTestSuite.class);
        TestingExtension testing = project.getExtensions().create(TestingExtension.class, "testing", DefaultTestingExtension.class, testSuites);

        JavaPluginExtension java = project.getExtensions().getByType(JavaPluginExtension.class);
        testSuites.registerFactory(JvmTestSuite.class, name -> project.getObjects().newInstance(DefaultJvmTestSuite.class, name, java.getSourceSets(), project.getConfigurations(), project.getTasks()));

        project.afterEvaluate(p -> {
            testSuites.withType(DefaultJvmTestSuite.class).configureEach(testSuite -> {
                testSuite.addTestTarget(testSuite.getName() + "java8-pluginAutomatic");
            });
        });
    }
}
