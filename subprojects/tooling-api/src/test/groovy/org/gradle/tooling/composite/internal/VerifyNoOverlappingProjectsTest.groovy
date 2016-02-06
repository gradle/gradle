/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.tooling.composite.internal
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.tooling.model.GradleProject
import org.gradle.util.TextUtil
import org.junit.Rule
import spock.lang.Specification

class VerifyNoOverlappingProjectsTest extends Specification {
    @Rule final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()

    final gradleProjects = [] as Set
    def reason = new StringBuilder()
    def spec = new DefaultCompositeValidator.VerifyNoOverlappingProjects(reason)

    def "fails when a root project overlaps with another root project"() {
        def root = gradleProject("root")
        def overlap = gradleProject("root")
        gradleProjects.addAll([root, overlap])
        expect:
        fails()
        TextUtil.normaliseLineSeparators(reason.toString()) == """Projects in a composite must not have overlapping project directories. Project '${overlap.name}' with root (${overlap.projectDirectory}) overlaps with project 'root' (${root.projectDirectory}).
"""
    }

    def "fails when a subproject overlaps with another root project"() {
        def root = gradleProject("root")
        def overlap = gradleProject("other", root)
        def other = gradleProject("other")
        gradleProjects.addAll([root, other, overlap])
        expect:
        fails()
        TextUtil.normaliseLineSeparators(reason.toString()) == """Projects in a composite must not have overlapping project directories. Project '${overlap.name}' with root (${overlap.parent.projectDirectory}) overlaps with project '${other.name}' (${other.projectDirectory}).
"""
    }

    def "passes when subprojects overlap with another project but are not in a multi-project build"() {
        def root = gradleProject("root")
        def overlap = gradleProject("root/overlap")
        gradleProjects.addAll([root, overlap])
        expect:
        passes()
        reason.toString() == ""
    }

    def "passes when subprojects only overlap with their root"() {
        def root = gradleProject("root")
        def overlap = gradleProject("root/overlap", root)
        gradleProjects.addAll([root, overlap])
        expect:
        passes()
        reason.toString() == ""
    }

    def "passes when projects do not overlap"() {
        def root1 = gradleProject("root1")
        def root2 = gradleProject("root2")
        gradleProjects.addAll([root1, root2])
        expect:
        passes()
        reason.toString() == ""
    }

    def fails() {
        !passes()
    }

    def passes() {
        spec.isSatisfiedBy(gradleProjects)
    }

    GradleProject gradleProject(String projectDir, GradleProject rootProject=null) {
        GradleProject project = Mock()
        project.parent >> rootProject
        project.projectDirectory >> temporaryFolder.createDir(projectDir)
        project.name >> projectDir
        return project
    }
}
