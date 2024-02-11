/*
 * Copyright 2015 the original author or authors.
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


package org.gradle.integtests.tooling.r24


import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.gradle.BuildInvocations

class BuildInvocationsCrossVersionSpec extends ToolingApiSpecification {

    def "set task selector description taken from task with path that has lowest ordering"() {
        temporaryFolder.createFile('settings.gradle') << '''
          rootProject.name = 'TestProject'
          include 'sub'
        '''

        temporaryFolder.createFile('build.gradle') << '''
          task alpha {
            description = 'ALPHA from root project'
          }
          task beta {}
        '''

        temporaryFolder.createDir('sub')
        temporaryFolder.createFile('sub', 'build.gradle') << '''
          task alpha {
            description = 'ALPHA from sub project'
          }
          task beta {
            description = 'BETA from sub project'
          }
          task gamma {
            description = 'GAMMA from sub'
          }
        '''

        when:
        def buildInvocations = withConnection { ProjectConnection connection ->
            connection.getModel(BuildInvocations.class)
        }

        then:
        buildInvocations != null
        buildInvocations.taskSelectors.find { it.name == 'alpha' }.description == 'ALPHA from root project'
        buildInvocations.taskSelectors.find { it.name == 'beta' }.description == null
        buildInvocations.taskSelectors.find { it.name == 'gamma' }.description == 'GAMMA from sub'
    }

}
