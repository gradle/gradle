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
import org.gradle.api.tasks.TaskDependency
import org.gradle.internal.Factory
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.util.Matchers
import spock.lang.Specification

class DefaultResolvedArtifactTest extends Specification {

    def "artifacts are equal when module and artifact identifier are equal"() {
        def dependency = dep("group", "module1", "1.2")
        def dependencySameModule = dep("group", "module1", "1.2")
        def dependency2 = dep("group", "module2", "1-beta")
        def artifactSource = Stub(Factory)
        def ivyArt = Stub(IvyArtifactName)
        def artifactId = Stub(ComponentArtifactIdentifier)
        def otherArtifactId = Stub(ComponentArtifactIdentifier)
        def buildDependencies = Stub(TaskDependency)

        def artifact = new DefaultResolvedArtifact(dependency, ivyArt, artifactId, buildDependencies, artifactSource)
        def equalArtifact = new DefaultResolvedArtifact(dependencySameModule, Stub(IvyArtifactName), artifactId, Stub(TaskDependency), Stub(Factory))
        def differentModule = new DefaultResolvedArtifact(dependency2, ivyArt, artifactId, buildDependencies, artifactSource)
        def differentId = new DefaultResolvedArtifact(dependency, ivyArt, otherArtifactId, buildDependencies, artifactSource)

        expect:
        artifact Matchers.strictlyEqual(equalArtifact)
        artifact != differentModule
        artifact != differentId
    }

    def dep(String group, String moduleName, String version) {
        new DefaultModuleVersionIdentifier(group, moduleName, version)
    }
}
