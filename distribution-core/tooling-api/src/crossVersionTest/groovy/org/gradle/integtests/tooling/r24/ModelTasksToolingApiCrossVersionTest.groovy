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
import org.gradle.tooling.model.GradleProject

class ModelTasksToolingApiCrossVersionTest extends ToolingApiSpecification {

    def setup() {
        file('build.gradle') << '''
            model {
                tasks {
                    create("fromModel") {
                        doLast {
                            buildDir.mkdirs()
                            new File(buildDir, "output.txt") << "from model rule defined task"
                        }
                    }
                }
            }
        '''
    }

    def "tasks added using model rule are visible from tooling api"() {
        when:
        GradleProject project = withConnection { it.getModel(GradleProject.class) }

        then:
        project.tasks*.name.contains "fromModel"
    }

    def "tasks added using model rule can be executed"() {
        when:
        withConnection { connection ->
            def build = connection.newBuild()
            build.forTasks("fromModel")
            build.run()
        }

        then:
        file('build/output.txt').text == "from model rule defined task"
    }
}
