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

import org.gradle.api.Action
import org.gradle.api.internal.ClosureBackedAction
import org.gradle.integtests.fixtures.BuildIdsFixture
import org.gradle.integtests.fixtures.TaskOutputOriginBuildIdFixture
import org.gradle.integtests.fixtures.executer.GradleExecuter
import org.gradle.internal.id.UniqueId
import org.gradle.launcher.continuous.AbstractContinuousIntegrationTest
import org.junit.Rule

class ContinuousIncrementalBuildOutputOriginTest extends AbstractContinuousIntegrationTest {

    final List<Action<GradleExecuter>> afterExecute = []

    final GradleExecuter delegatingExecuter = new GradleExecuter() {

        @Delegate
        GradleExecuter delegate = executer

        void afterExecute(Closure action) {
            afterExecute << new ClosureBackedAction<GradleExecuter>(action)
        }

    }

    @Rule
    public final BuildIdsFixture buildIdFixture = new BuildIdsFixture(delegatingExecuter, temporaryFolder)

    @Rule
    public final TaskOutputOriginBuildIdFixture originBuildIdFixture = new TaskOutputOriginBuildIdFixture(delegatingExecuter, temporaryFolder)

    UniqueId getBuildId() {
        buildIdFixture.buildId
    }

    UniqueId originBuildId(String taskPath) {
        originBuildIdFixture.originId(taskPath)
    }

    void afterBuild() {
        afterExecute*.execute(executer)
    }

    def "new ID is assigned for each execution"() {
        given:
        def i1 = file("i1")
        def i2 = file("i2")

        when:
        i1.text = "1"
        i2.text = "1"

        buildFile << """
            task t1 {
                def o = file("o1")
                def i = file("i1")
                inputs.files i
                outputs.files o
                doLast {
                    println "t1: " + i.text
                    o.text = i.text
                }
            }
            task t2 {
                def o = file("o2")
                def i = file("i2")
                inputs.files i
                outputs.files o
                doLast {
                    println "t2: " + i.text
                    o.text = i.text
                }
            }
            task t {
                dependsOn t1, t2
            }
        """

        then:
        succeeds("t")
        afterBuild()
        buildIdFixture.buildId != null

        when:
        update(i1, "2")

        then:
        succeeds()
        afterBuild()
        originBuildId(":t1") == null
        originBuildId(":t2") == buildIdFixture.buildIds[0]

        when:
        update(i2, "2")

        then:
        succeeds()
        afterBuild()
        originBuildId(":t1") == buildIdFixture.buildIds[1]
        originBuildId(":t2") == null
    }

}
