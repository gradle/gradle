/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.artifacts

import org.gradle.api.UnknownProjectException
import org.gradle.api.internal.artifacts.dsl.CapabilityNotationParserFactory
import org.gradle.api.internal.artifacts.dsl.dependencies.ProjectFinder
import org.gradle.api.internal.artifacts.dsl.dependencies.UnknownProjectFinder
import org.gradle.api.internal.project.ProjectIdentity
import org.gradle.api.internal.project.ProjectState
import org.gradle.api.internal.project.ProjectStateLookup
import org.gradle.api.problems.internal.ExceptionProblemRegistry
import org.gradle.api.problems.internal.ProblemInternal
import org.gradle.api.problems.internal.ProblemReporterInternal
import org.gradle.api.problems.internal.ProblemsInternal
import org.gradle.util.AttributeTestUtil
import org.gradle.util.Path
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultProjectDependencyFactoryTest extends Specification {

    def projects = [
        projectState(ProjectIdentity.forRootProject(Path.ROOT, "root")),
        projectState(ProjectIdentity.forSubproject(Path.ROOT, Path.path(":app"))),
        projectState(ProjectIdentity.forRootProject(Path.path(":lib"), "lib")),
        projectState(ProjectIdentity.forSubproject(Path.path(":lib"), Path.path(":core"))),
    ]

    def projectStateLookup = Stub(ProjectStateLookup) {
        getAllProjects() >> projects
        findProject(_ as Path) >> { Path path -> projects.find { it.identityPath == path } }
    }

    def problems = Stub(ProblemsInternal) {
        getGroups() >> TestUtil.problemsService().groups
        getInternalReporter() >> Stub(ProblemReporterInternal) {
            internalCreate(_) >> Stub(ProblemInternal)
        }
    }

    def factory = new DefaultProjectDependencyFactory(
        TestUtil.instantiatorFactory().decorateLenient(),
        new CapabilityNotationParserFactory(false).create(),
        TestUtil.objectFactory(),
        AttributeTestUtil.attributesFactory(),
        projectStateLookup,
        new UnknownProjectFinder("Project dependencies cannot be declared here."),
        problems,
        new ExceptionProblemRegistry()
    )

    def "suggests close build tree paths for unknown identity path"() {
        when:
        factory.create(Path.path(":lib:cor"))

        then:
        def e = thrown(UnknownProjectException)
        e.message == "Project with path ':lib:cor' could not be found. Some candidates are: ':lib:core'."
    }

    def "suggests close paths within the build of the given finder"() {
        def libFinder = Stub(ProjectFinder) {
            resolveIdentityPath(":cor") >> Path.path(":lib:cor")
            getBuildPath() >> Path.path(":lib")
        }

        when:
        factory.create(":cor", libFinder)

        then:
        def e = thrown(UnknownProjectException)
        e.message == "Project with path ':lib:cor' could not be found. Some candidates are: ':core'."
    }

    def "creates dependency using the given finder"() {
        def libFinder = Stub(ProjectFinder) {
            resolveIdentityPath(":core") >> Path.path(":lib:core")
        }

        expect:
        factory.create(":core", libFinder).path == ":core"
    }

    private ProjectState projectState(ProjectIdentity identity) {
        Stub(ProjectState) {
            getIdentity() >> identity
            getIdentityPath() >> identity.buildTreePath
            getProjectPath() >> identity.projectPath
        }
    }
}
