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

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.HelperUtil
import org.junit.Rule
import spock.lang.Specification

class GradleProjectBuilderTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
    final def builder = new GradleProjectBuilder()

    def "builds basics for project"() {
        def buildFile = tmpDir.file("build.gradle") << "//empty"
        def project = HelperUtil.builder().withName("test").withProjectDir(tmpDir.testDirectory).build()
        project.description = 'a test project'

        when:
        def model = builder.buildAll(project)

        then:
        model.path == ':'
        model.name == 'test'
        model.description == 'a test project'
        model.buildScript.sourceFile == buildFile
    }
}
