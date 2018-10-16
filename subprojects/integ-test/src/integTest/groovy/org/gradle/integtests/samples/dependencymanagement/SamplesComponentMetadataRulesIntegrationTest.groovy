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
import org.gradle.util.Requires
import org.junit.Rule
import spock.lang.Unroll

import static org.gradle.util.TestPrecondition.KOTLIN_SCRIPT

@Requires(KOTLIN_SCRIPT)
class SamplesComponentMetadataRulesIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    @Unroll
    @UsesSample('userguide/dependencyManagement/customizingResolution/metadataRule')
    def "can run custom status scheme sample with #dsl dsl" () {
        executer.inDirectory(sample.dir.file(dsl))

        when:
        succeeds "listApi"

        then:
        output.contains("Resolved: api-2.0.jar")

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample('userguide/dependencyManagement/customizingResolution/metadataRule')
    def "can run custom status scheme with module sample with #dsl dsl" () {
        executer.inDirectory(sample.dir.file(dsl))

        when:
        succeeds "listLib"

        then:
        output.contains("Resolved: lib-1.9.jar")

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample('userguide/dependencyManagement/customizingResolution/metadataRule')
    def "can run ivy metadata rule with #dsl dsl" () {
        executer.inDirectory(sample.dir.file(dsl))

        when:
        succeeds "listWithIvyRule"

        then:
        output.contains("Resolved: lib-2.0.jar")

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample('userguide/dependencyManagement/customizingResolution/metadataRule')
    def "can run custom status scheme with parameterized class rule sample with #dsl dsl" () {
        executer.inDirectory(sample.dir.file(dsl))

        when:
        succeeds "listWithConfiguredRule"

        then:
        output.contains("Resolved: api-1.9.jar")

        where:
        dsl << ['groovy', 'kotlin']
    }
}
