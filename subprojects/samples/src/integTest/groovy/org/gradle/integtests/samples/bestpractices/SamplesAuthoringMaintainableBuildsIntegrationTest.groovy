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

package org.gradle.integtests.samples.bestpractices

import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.UsesSample
import org.junit.Rule
import spock.lang.Unroll

class SamplesAuthoringMaintainableBuildsIntegrationTest extends AbstractSampleIntegrationTest {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    @Unroll
    @UsesSample('userguide/bestPractices/taskDefinition')
    def "can execute tasks with #dsl dsl"() {
        executer.inDirectory(sample.dir.file(dsl))

        when:
        succeeds 'allDocs'

        then:
        outputContains('Generating all documentation...')

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample('userguide/bestPractices/taskGroupDescription')
    @ToBeFixedForInstantExecution
    def "can render a task's group and description in tasks report with #dsl dsl"() {
        executer.inDirectory(sample.dir.file(dsl))

        when:
        succeeds 'tasks'

        then:
        outputContains("""Documentation tasks
-------------------
generateDocs - Generates the HTML documentation for this project.""")

        where:
        dsl << ['groovy', 'kotlin']
    }

    @Unroll
    @UsesSample('userguide/bestPractices/logicDuringConfiguration')
    @ToBeFixedForInstantExecution(iterationMatchers = ".*kotlin dsl.*")
    def "can execute logic during execution phase with #dsl dsl"() {
        executer.inDirectory(sample.dir.file("$subDirName/$dsl"))

        when:
        succeeds 'printArtifactNames'

        then:
        outputContains('log4j-1.2.17.jar')

        where:
        dsl      | subDirName
        'groovy' | 'do'
        'kotlin' | 'do'
    }

    @Unroll
    @UsesSample('userguide/bestPractices/logicDuringConfiguration')
    def "throw exception when executing logic during configuration phrase with #dsl dsl"() {
        executer.inDirectory(sample.dir.file("$subDirName/$dsl"))

        when:
        fails 'printArtifactNames'

        then:
        failureCauseContains("You shouldn't resolve configurations during configuration phase")

        where:
        dsl      | subDirName
        'groovy' | 'dont'
        'kotlin' | 'dont'
    }

    @Unroll
    @UsesSample('userguide/bestPractices/conditionalLogic')
    def "can execute conditional logic for #exampleName with #dsl dsl"() {
        executer.inDirectory(sample.dir.file("$subDirName/$dsl"))
        executer.withArgument('-PreleaseEngineer=true')

        when:
        succeeds 'release'

        then:
        outputContains('Releasing to production...')

        where:
        dsl      | subDirName | exampleName
        'groovy' | 'dont'     | 'negative example'
        'kotlin' | 'dont'     | 'negative example'
        'groovy' | 'do'       | 'positive example'
        'kotlin' | 'do'       | 'positive example'
    }
}
