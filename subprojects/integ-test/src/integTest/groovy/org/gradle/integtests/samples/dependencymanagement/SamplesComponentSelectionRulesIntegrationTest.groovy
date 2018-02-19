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
import org.gradle.integtests.fixtures.UsesSample
import org.junit.Rule

class SamplesComponentSelectionRulesIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    @UsesSample("userguide/dependencyManagement/customizingResolution/selectionRule")
    def "can run resolveConfiguration sample"() {
        executer.inDirectory(sample.dir)

        when:
        run "resolveConfiguration"

        then:
        output.contains "Rejected version: 1.5"
        output.contains "Rejected version: 1.4"
        output.contains "** Accepted version: 1.3.0"
    }

    @UsesSample("userguide/dependencyManagement/customizingResolution/selectionRule")
    def "can run reject sample"() {
        executer.inDirectory(sample.dir)

        when:
        run "printRejectConfig"

        then:
        output.contains "Resolved: api-1.4.jar"
    }

    @UsesSample("userguide/dependencyManagement/customizingResolution/selectionRule")
    def "can run metadata rules sample"() {
        executer.inDirectory(sample.dir)

        when:
        run "printMetadataRulesConfig"

        then:
        output.contains "Resolved: api-1.3.0.jar"
        output.contains "Resolved: lib-1.9.jar"
    }

    @UsesSample("userguide/dependencyManagement/customizingResolution/selectionRule")
    def "can run targeted rule sample"() {
        executer.inDirectory(sample.dir)

        when:
        run "printTargetConfig"

        then:
        output.contains "Resolved: api-1.4.jar"
    }

    @UsesSample("userguide/dependencyManagement/customizingResolution/selectionRule")
    def "can run rules source sample"() {
        executer.inDirectory(sample.dir)

        when:
        run "printRuleSourceConfig"

        then:
        output.contains "Resolved: api-1.4.jar"
    }
}
