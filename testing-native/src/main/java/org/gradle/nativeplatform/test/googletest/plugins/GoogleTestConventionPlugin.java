/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.nativeplatform.test.googletest.plugins;

import org.gradle.api.Incubating;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.model.Defaults;
import org.gradle.model.ModelMap;
import org.gradle.model.RuleSource;
import org.gradle.nativeplatform.NativeComponentSpec;
import org.gradle.nativeplatform.test.googletest.GoogleTestTestSuiteSpec;
import org.gradle.nativeplatform.test.internal.NativeTestSuites;
import org.gradle.testing.base.TestSuiteContainer;

/**
 * A plugin that applies the {@link GoogleTestPlugin} and adds conventions on top of it.
 */
@Incubating
public class GoogleTestConventionPlugin implements Plugin<Project> {


    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(GoogleTestPlugin.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {

        @Defaults
        public void createCUnitTestSuitePerComponent(TestSuiteContainer testSuites, ModelMap<NativeComponentSpec> components) {
            NativeTestSuites.createConventionalTestSuites(testSuites, components, GoogleTestTestSuiteSpec.class);
        }
    }

}
