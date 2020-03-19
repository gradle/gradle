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
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.UsesSample
import org.junit.Rule
import spock.lang.Unroll

class SamplesDefiningUsingConfigurationsIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    def setup() {
        executer.withRepositoryMirrors()
    }

    @Unroll
    @UsesSample("userguide/dependencyManagement/definingUsingConfigurations/custom")
    @ToBeFixedForInstantExecution(iterationMatchers = ".*kotlin dsl")
    def "can declare and resolve custom configuration with #dsl dsl"() {
        setup:
        executer.inDirectory(sample.dir.file(dsl))
        executer.requireGradleDistribution() // required to avoid multiple Servlet API JARs in classpath

        when:
        succeeds('preCompileJsps')

        then:
        sample.dir.file("$dsl/build/compiled-jsps/org/apache/jsp/hello_jsp.java").isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample("userguide/dependencyManagement/definingUsingConfigurations/inheritance")
    def "can extend one configuration from another configuration"() {
        setup:
        executer.inDirectory(sample.dir.file(dsl))

        when:
        succeeds('copyLibs')

        then:
        sample.dir.file("$dsl/build/libs/junit-4.12.jar").isFile()
        sample.dir.file("$dsl/build/libs/httpclient-4.5.5.jar").isFile()

        where:
        dsl << ['groovy', 'kotlin']
    }
}
