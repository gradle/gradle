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

import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UsesSample
import org.gradle.integtests.fixtures.AbstractSampleIntegrationTest
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Unroll

class SamplesAuthoringMaintainableBuildsIntegrationTest extends AbstractSampleIntegrationTest {

    @Rule
    Sample sample = new Sample(testDirectoryProvider)

    @UsesSample('userguide/bestPractices/taskDefinition')
    def "can execute tasks"() {
        executer.inDirectory(sample.dir)

        when:
        succeeds 'allDocs'

        then:
        outputContains('Generating all documentation...')
    }

    @UsesSample('userguide/bestPractices/taskGroupDescription')
    def "can render a task's group and description in tasks report"() {
        executer.inDirectory(sample.dir)

        when:
        succeeds 'tasks'

        then:
        outputContains("""Documentation tasks
-------------------
generateDocs - Generates the HTML documentation for this project.""")
    }

    @Unroll
    @UsesSample('userguide/bestPractices/logicDuringConfiguration')
    @Ignore('Eagerly resolve artifacts during configuration phase')
    def "can execute logic during #lifecyclePhase"() {
        executer.inDirectory(new File(sample.dir, subDirName))

        when:
        succeeds 'printArtifactNames'

        then:
        outputContains('log4j-1.2.17.jar')

        where:
        subDirName | lifecyclePhase
        'dont'     | 'configuration phase'
        'do'       | 'execution phase'
    }

    @Unroll
    @UsesSample('userguide/bestPractices/conditionalLogic')
    def "can execute conditional logic for #exampleName"() {
        executer.inDirectory(new File(sample.dir, subDirName))
        executer.withArgument('-PreleaseEngineer=true')

        when:
        succeeds 'release'

        then:
        outputContains('Releasing to production...')

        where:
        subDirName | exampleName
        'dont'     | 'negative example'
        'do'       | 'positive example'
    }
}
