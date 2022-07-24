/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.language.cpp.plugins.CppLangPlugin;
import org.gradle.model.ModelMap;
import org.gradle.model.Path;
import org.gradle.model.RuleSource;
import org.gradle.nativeplatform.test.googletest.GoogleTestTestSuiteBinarySpec;
import org.gradle.nativeplatform.test.googletest.GoogleTestTestSuiteSpec;
import org.gradle.nativeplatform.test.googletest.internal.DefaultGoogleTestTestSuiteBinary;
import org.gradle.nativeplatform.test.googletest.internal.DefaultGoogleTestTestSuiteSpec;
import org.gradle.nativeplatform.test.plugins.NativeBinariesTestPlugin;
import org.gradle.platform.base.ComponentBinaries;
import org.gradle.platform.base.ComponentType;
import org.gradle.platform.base.TypeBuilder;

import java.io.File;

import static org.gradle.nativeplatform.test.internal.NativeTestSuites.createNativeTestSuiteBinaries;

/**
 * A plugin that sets up the infrastructure for testing native binaries with GoogleTest.
 */
@Incubating
public class GoogleTestPlugin implements Plugin<Project> {

    @Override
    public void apply(final Project project) {
        project.getPluginManager().apply(NativeBinariesTestPlugin.class);
        project.getPluginManager().apply(CppLangPlugin.class);
    }

    @SuppressWarnings("UnusedDeclaration")
    static class Rules extends RuleSource {

        @ComponentType
        public void registerGoogleTestSuiteSpecTest(TypeBuilder<GoogleTestTestSuiteSpec> builder) {
            builder.defaultImplementation(DefaultGoogleTestTestSuiteSpec.class);
        }

        @ComponentType
        public void registerGoogleTestSuiteBinaryType(TypeBuilder<GoogleTestTestSuiteBinarySpec> builder) {
            builder.defaultImplementation(DefaultGoogleTestTestSuiteBinary.class);
        }

        @ComponentBinaries
        public void createGoogleTestTestBinaries(ModelMap<GoogleTestTestSuiteBinarySpec> binaries,
                                                 GoogleTestTestSuiteSpec testSuite,
                                                 @Path("buildDir") final File buildDir,
                                                 final ServiceRegistry serviceRegistry) {
            createNativeTestSuiteBinaries(binaries, testSuite, GoogleTestTestSuiteBinarySpec.class, "GoogleTestExe", buildDir, serviceRegistry);
       }
    }
}
