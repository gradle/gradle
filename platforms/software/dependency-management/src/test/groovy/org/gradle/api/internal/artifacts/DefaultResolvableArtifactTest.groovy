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
import org.gradle.internal.component.model.IvyArtifactName
import org.gradle.internal.model.CalculatedValue
import org.gradle.util.Matchers
import org.gradle.util.TestUtil
import spock.lang.Specification

class DefaultResolvableArtifactTest extends Specification {
    def calculatedValueContainerFactory = TestUtil.calculatedValueContainerFactory()

    def "artifacts are equal when artifact identifier is equal"() {
        def dependency = dep("group", "module1", "1.2")
        def dependencySameModule = dep("group", "module1", "1.2")
        def dependency2 = dep("group", "module2", "1-beta")
        def artifactSource = Stub(CalculatedValue)
        def ivyArt = Stub(IvyArtifactName)
        def artifactId = Stub(ComponentArtifactIdentifier)
        def otherArtifactId = Stub(ComponentArtifactIdentifier)
        def buildDependencies = Stub(TaskDependencyContainer)

        def artifact = new DefaultResolvableArtifact(dependency, ivyArt, artifactId, buildDependencies, artifactSource, calculatedValueContainerFactory)
        def equalArtifact = new DefaultResolvableArtifact(dependencySameModule, Stub(IvyArtifactName), artifactId, Stub(TaskDependencyContainer), Stub(CalculatedValue), calculatedValueContainerFactory)
        def differentModule = new DefaultResolvableArtifact(dependency2, ivyArt, artifactId, buildDependencies, artifactSource, calculatedValueContainerFactory)
        def differentId = new DefaultResolvableArtifact(dependency, ivyArt, otherArtifactId, buildDependencies, artifactSource, calculatedValueContainerFactory)

        expect:
        artifact Matchers.strictlyEqual(equalArtifact)
        artifact Matchers.strictlyEqual(differentModule)
        artifact != differentId
    }

    def dep(String group, String moduleName, String version) {
        new DefaultModuleVersionIdentifier(group, moduleName, version)
    }
}
