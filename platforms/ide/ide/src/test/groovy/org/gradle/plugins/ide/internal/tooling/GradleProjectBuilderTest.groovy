/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.plugins.ide.internal.tooling


import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class GradleProjectBuilderTest extends AbstractProjectBuilderSpec {
    def builder = new GradleProjectBuilder()

    def "builds basics for project"() {
        def buildFile = temporaryFolder.file("build.gradle") << "//empty"
        project.description = 'a test project'

        when:
        def model = builder.buildRoot(project)

        then:
        model.path == ':'
        model.name == 'test-project'
        model.description == 'a test project'
        model.buildDirectory == project.buildDir
        model.buildScript.sourceFile == buildFile
    }
}
