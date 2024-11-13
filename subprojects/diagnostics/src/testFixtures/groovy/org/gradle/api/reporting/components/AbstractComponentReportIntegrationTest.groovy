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
package org.gradle.api.reporting.components

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.StableConfigurationCacheDeprecations
import org.gradle.internal.InternalTransformer
import org.gradle.nativeplatform.fixtures.AvailableToolChains

abstract class AbstractComponentReportIntegrationTest extends AbstractIntegrationSpec implements StableConfigurationCacheDeprecations {
    InternalTransformer<String, String> formatter = new ComponentReportOutputFormatter()
    JavaVersion currentJvm = JavaVersion.current()
    String currentJavaName = "java" + currentJvm.majorVersion
    String currentJava = "Java SE " + currentJvm.majorVersion
    String currentJdk = String.format("JDK %s (%s)", currentJvm.majorVersion, currentJvm);

    def setup() {
        settingsFile << "rootProject.name = 'test'"
    }

    boolean outputMatches(String expectedOutput) {
        def actualOutput = result.groupedOutput.task(":components").output
        assert removeIrrelevantOutput(actualOutput) == expected(expectedOutput)
        return true
    }

    String removeIrrelevantOutput(String output) {
        return output.readLines().findAll {
            !it.isEmpty() && !(it ==~ /^Download http.*$/) && !(it ==~ /.*has been deprecated.*$/)
        }.join('\n')
    }

    String expected(String normalised) {
        String raw = """------------------------------------------------------------
Root project 'test'
------------------------------------------------------------
""" + normalised + """
Note: currently not all plugins register their components, so some components may not be visible here."""
        return formatter.transform(raw).readLines().findAll { !it.isEmpty() }.join('\n')
    }

    AvailableToolChains.InstalledToolChain getToolChain() {
        return AvailableToolChains.defaultToolChain
    }
}
