/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.samples.dependencymanagement

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.util.Requires
import org.junit.Rule

import static org.gradle.util.TestPrecondition.JDK8_OR_LATER

class SamplesDefiningUsingConfigurationsIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    def setup() {
        executer.usingInitScript(RepoScriptBlockUtil.createMirrorInitScript())
    }

    @Requires(JDK8_OR_LATER)
    @UsesSample("userguide/dependencyManagement/definingUsingConfigurations/custom")
    def "can declare and resolve custom configuration"() {
        setup:
        executer.inDirectory(sample.dir)
        executer.requireGradleDistribution() // required to avoid multiple Servlet API JARs in classpath

        when:
        succeeds('preCompileJsps')

        then:
        sample.dir.file('build/compiled-jsps/org/apache/jsp/hello_jsp.java').isFile()
    }

    @UsesSample("userguide/dependencyManagement/definingUsingConfigurations/inheritance")
    def "can extend one configuration from another configuration"() {
        setup:
        executer.inDirectory(sample.dir)

        when:
        succeeds('copyLibs')

        then:
        sample.dir.file('build/libs/junit-4.12.jar').isFile()
        sample.dir.file('build/libs/httpclient-4.5.5.jar').isFile()
    }
}
