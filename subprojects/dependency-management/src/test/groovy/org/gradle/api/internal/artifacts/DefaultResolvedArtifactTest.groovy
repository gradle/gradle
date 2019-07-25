/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.artifacts.component.ComponentArtifactIdentifier
import org.gradle.api.internal.tasks.TaskDependencyContainer
import org.gradle.internal.Factory
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.util.Matchers
import spock.lang.Specification

class DefaultResolvedArtifactTest extends Specification {

    def "artifacts are equal when artifact identifier is equal"() {
        def dependency = dep("group", "module1", "1.2")
        def dependencySameModule = dep("group", "module1", "1.2")
        def dependency2 = dep("group", "module2", "1-beta")
        def artifactSource = Stub(Factory)
        def ivyArt = Stub(IvyArtifactName)
        def artifactId = Stub(ComponentArtifactIdentifier)
        def otherArtifactId = Stub(ComponentArtifactIdentifier)
        def buildDependencies = Stub(TaskDependencyContainer)

        def artifact = new DefaultResolvedArtifact(dependency, ivyArt, artifactId, buildDependencies, artifactSource)
        def equalArtifact = new DefaultResolvedArtifact(dependencySameModule, Stub(IvyArtifactName), artifactId, Stub(TaskDependencyContainer), Stub(Factory))
        def differentModule = new DefaultResolvedArtifact(dependency2, ivyArt, artifactId, buildDependencies, artifactSource)
        def differentId = new DefaultResolvedArtifact(dependency, ivyArt, otherArtifactId, buildDependencies, artifactSource)

        expect:
        artifact Matchers.strictlyEqual(equalArtifact)
        artifact Matchers.strictlyEqual(differentModule)
        artifact != differentId
    }

    def "resolves file once and reuses result"() {
        def dependency = dep("group", "module1", "1.2")
        def artifactSource = Mock(Factory)
        def ivyArt = Stub(IvyArtifactName)
        def artifactId = Stub(ComponentArtifactIdentifier)
        def buildDependencies = Stub(TaskDependencyContainer)
        def file = new File("result")

        when:
        def artifact = new DefaultResolvedArtifact(dependency, ivyArt, artifactId, buildDependencies, artifactSource)

        then:
        !artifact.resolveSynchronously

        when:
        def result = artifact.file

        then:
        result == file
        artifact.resolveSynchronously

        and:
        1 * artifactSource.create() >> file
        0 * artifactSource._

        when:
        result = artifact.file

        then:
        result == file
        0 * artifactSource._
    }

    def "resolves file once and reuses failure"() {
        def dependency = dep("group", "module1", "1.2")
        def artifactSource = Mock(Factory)
        def ivyArt = Stub(IvyArtifactName)
        def artifactId = Stub(ComponentArtifactIdentifier)
        def buildDependencies = Stub(TaskDependencyContainer)
        def failure = new RuntimeException()

        when:
        def artifact = new DefaultResolvedArtifact(dependency, ivyArt, artifactId, buildDependencies, artifactSource)

        then:
        !artifact.resolveSynchronously

        when:
        artifact.file

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        artifact.resolveSynchronously

        and:
        1 * artifactSource.create() >> { throw failure }
        0 * artifactSource._

        when:
        artifact.file

        then:
        def e2 = thrown(RuntimeException)
        e2 == failure
        0 * artifactSource._
    }

    def dep(String group, String moduleName, String version) {
        new DefaultModuleVersionIdentifier(group, moduleName, version)
    }
}
