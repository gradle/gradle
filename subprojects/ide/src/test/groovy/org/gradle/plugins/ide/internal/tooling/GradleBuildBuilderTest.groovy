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

import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.ProjectBuilder
import org.gradle.util.TestUtil
import org.gradle.util.UsesNativeServices
import org.junit.ClassRule
import spock.lang.Shared
import spock.lang.Specification

@UsesNativeServices
@CleanupTestDirectory
class GradleBuildBuilderTest extends Specification {
    @Shared
    @ClassRule
    public TestNameTestDirectoryProvider temporaryFolder = TestNameTestDirectoryProvider.newInstance()
    def builder = new GradleBuildBuilder()
    @Shared def project = TestUtil.builder(temporaryFolder).withName("root").build()
    @Shared def child1 = ProjectBuilder.builder().withName("child1").withParent(project).build()
    @Shared def child2 = ProjectBuilder.builder().withName("child2").withParent(project).build()

    def "builds model"() {
        expect:
        def model = builder.buildAll("org.gradle.tooling.model.gradle.GradleBuild", startProject)
        model.rootProject.path == ":"
        model.rootProject.name == "root"
        model.rootProject.parent == null
        model.rootProject.projectDirectory == project.projectDir
        model.rootProject.children.size() == 2
        model.rootProject.children.every { it.parent == model.rootProject }
        model.projects*.name == ["root", "child1", "child2"]
        model.projects*.path == [":", ":child1", ":child2"]

        where:
        startProject | _
        project      | _
        child2       | _
    }
}
