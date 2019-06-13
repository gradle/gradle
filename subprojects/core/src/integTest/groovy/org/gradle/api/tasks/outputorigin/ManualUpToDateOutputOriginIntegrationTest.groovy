/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.tasks.outputorigin

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.OriginFixture
import org.gradle.integtests.fixtures.ScopeIdsFixture
import org.junit.Rule

class ManualUpToDateOutputOriginIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final ScopeIdsFixture scopeIds = new ScopeIdsFixture(executer, temporaryFolder)

    @Rule
    public final OriginFixture originBuildInvocationId = new OriginFixture(executer, temporaryFolder)

    String getBuildInvocationId() {
        scopeIds.buildInvocationId.asString()
    }

    String originBuildInvocationId(String taskPath) {
        originBuildInvocationId.originId(taskPath)
    }

    def "considers invocation that manually declared up-to-date to be an origin"() {
        given:
        buildScript """
            class CustomTask extends DefaultTask {
                @Input
                String value = "a"
                
                @OutputFile
                File file = project.file("out.txt")
                
                @TaskAction
                void action() {
                    if (file.file) {
                        didWork = false
                    } else {
                        file.text = value
                    }
                }
            }
            
            def write = tasks.create("write", CustomTask)
        """

        when:
        succeeds "write"

        then:
        executedAndNotSkipped ":write"
        originBuildInvocationId(":write") == null

        when:
        buildFile << """
            write.value = "b"
        """
        succeeds("write")

        then:
        skipped(":write")
        def secondBuildId = buildInvocationId
        originBuildInvocationId(":write") == null

        when:
        succeeds("write")

        then:
        skipped(":write")
        originBuildInvocationId(":write") == secondBuildId
    }

}
