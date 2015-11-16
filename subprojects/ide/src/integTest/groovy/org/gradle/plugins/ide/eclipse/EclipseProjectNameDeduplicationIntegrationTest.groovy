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

package org.gradle.plugins.ide.eclipse

import org.gradle.plugins.ide.AbstractIdeDeduplicationIntegrationTest

class EclipseProjectNameDeduplicationIntegrationTest extends AbstractIdeDeduplicationIntegrationTest {
    @Override
    protected String projectName(String project) {
        new EclipseProjectFixture(testDirectory.file(project)).getProjectName()
    }

    @Override
    protected String getIdeName() {
        return "eclipse"
    }

    def "explicit configured eclipse project name is used"() {
        given:
        project("root") {
            project("foo") {
                project("app") {
                    buildFile << "eclipse.project.name = 'custom-app'"
                }
            }
            project("bar") {
                project("app") {}
            }
        }

        when:
        run ideName

        then:
        projectName("foo") == "foo"
        projectName("foo/app") == "custom-app"
        projectName("bar") == "bar"
        projectName("bar/app") == "app"
    }
}
