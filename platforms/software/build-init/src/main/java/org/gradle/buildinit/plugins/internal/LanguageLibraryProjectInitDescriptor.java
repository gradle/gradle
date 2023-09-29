/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.buildinit.plugins.internal;

import org.gradle.api.plugins.internal.JvmTestSuitePluginHelper;
import org.gradle.buildinit.plugins.internal.modifiers.BuildInitTestFramework;
import org.gradle.buildinit.plugins.internal.modifiers.ModularizationOption;

import java.util.Collections;
import java.util.Optional;
import java.util.Set;

public abstract class LanguageLibraryProjectInitDescriptor implements LanguageSpecificProjectGenerator {
    protected String withPackage(InitSettings settings, String className) {
        if (settings.getPackageName().isEmpty()) {
            return className;
        } else {
            return settings.getPackageName() + "." + className;
        }
    }

    @Override
    public Set<ModularizationOption> getModularizationOptions() {
        return Collections.singleton(ModularizationOption.SINGLE_PROJECT);
    }

    @Override
    public Optional<String> getFurtherReading(InitSettings settings) {
        return Optional.empty();
    }

    protected BuildScriptBuilder.SuiteSpec configureDefaultTestSuite(BuildScriptBuilder buildScriptBuilder, BuildInitTestFramework testFramework, TemplateLibraryVersionProvider libraryVersionProvider) {
        return addTestSuite(JvmTestSuitePluginHelper.DEFAULT_TEST_SUITE_NAME, buildScriptBuilder, testFramework, libraryVersionProvider);
    }

    protected BuildScriptBuilder.SuiteSpec addTestSuite(String name, BuildScriptBuilder buildScriptBuilder, BuildInitTestFramework testFramework, TemplateLibraryVersionProvider libraryVersionProvider) {
        switch (testFramework) {
            case JUNIT:
                return buildScriptBuilder.testing().junitSuite(name, libraryVersionProvider);
            case JUNIT_JUPITER:
                return buildScriptBuilder.testing().junitJupiterSuite(name, libraryVersionProvider);
            case SPOCK:
                return buildScriptBuilder.testing().spockSuite(name, libraryVersionProvider);
            case KOTLINTEST:
                return buildScriptBuilder.testing().kotlinTestSuite(name, libraryVersionProvider);
            case TESTNG:
                return buildScriptBuilder.testing().testNG(name, libraryVersionProvider);
            case SCALATEST:
                BuildScriptBuilder.SuiteSpec suiteSpec = buildScriptBuilder.testing().junitSuite(name, libraryVersionProvider);
                String scalaVersion = libraryVersionProvider.getVersion("scala");
                String scalaTestVersion = libraryVersionProvider.getVersion("scalatest");
                String scalaTestPlusJunitVersion = libraryVersionProvider.getVersion("scalatestplus-junit");
                String scalaXmlVersion = libraryVersionProvider.getVersion("scala-xml");
                suiteSpec.implementation("Use Scalatest for testing our library",
                        "org.scalatest:scalatest_" + scalaVersion + ":" + scalaTestVersion,
                        "org.scalatestplus:junit-4-13_" + scalaVersion + ":" + scalaTestPlusJunitVersion);
                suiteSpec.runtimeOnly("Need scala-xml at test runtime",
                        "org.scala-lang.modules:scala-xml_" + scalaVersion + ":" + scalaXmlVersion);
                return suiteSpec;
            default:
                throw new IllegalArgumentException(testFramework + " is not yet supported.");
        }
    }
}
