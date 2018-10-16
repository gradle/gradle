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

package org.gradle.integtests.tooling.r33

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.ListenerFailedException
import org.gradle.tooling.ProjectConnection
import org.gradle.util.GradleVersion
import spock.lang.Issue

@TargetGradleVersion('>=3.3')
class NestedBuildsProgressCrossVersionSpec extends ToolingApiSpecification {

    @Issue("https://github.com/gradle/gradle/issues/2622")
    def "does not break for nested builds"() {
        given:
        def events = ProgressEvents.create()
        def caughtException = null
        buildFile << """          
            task nested1(type: GradleBuild)
            
            task nested2(type: GradleBuild) {
                tasks = ['nested1']
            }           
        """
        settingsFile << "rootProject.name='root'"

        when:
        try {
            withConnection {
                ProjectConnection connection ->
                    connection.newBuild()
                        .forTasks('nested2')
                        .addProgressListener(events)
                        .run()
            }
        } catch (ListenerFailedException e) {
            caughtException = e.cause
        }

        then:
        if (targetVersion.baseVersion >= GradleVersion.version("4.0") && targetVersion.baseVersion < GradleVersion.version("4.2")) {
            //this is a regression we accepted in 4.0.x and 4.1.x
            assert caughtException instanceof IllegalStateException
            assert caughtException.message =~ "Operation org\\.gradle\\.tooling\\.internal\\.provider\\.events\\.DefaultOperationDescriptor.* already available."
        } else {
            assert !caughtException
        }
    }
}
