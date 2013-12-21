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

import org.gradle.api.artifacts.ResolvedModuleVersion
import org.gradle.api.internal.artifacts.metadata.IvyArtifactName
import org.gradle.internal.Factory
import org.gradle.util.Matchers
import spock.lang.Specification

class DefaultResolvedArtifactTest extends Specification {
    final Factory artifactSource = Mock()

    def "artifacts are equal when module and name are equal"() {
        def dependency = dep("group", "module1", "1.2")
        def dependencySameModule = dep("group", "module1", "1.2")
        def dependency2 = dep("group", "module2", "1-beta")
        def ivyArt = Stub(IvyArtifactName)
        def artifact = new DefaultResolvedArtifact(dependency, {} as Factory, ivyArt, artifactSource, 0)
        def equalArtifact = new DefaultResolvedArtifact(dependencySameModule, {} as Factory, ivyArt, artifactSource, 0)
        def differentModule = new DefaultResolvedArtifact(dependency2, {} as Factory, ivyArt, artifactSource, 0)
        def differentName = new DefaultResolvedArtifact(dependency, {} as Factory, Stub(IvyArtifactName), artifactSource, 0)

        expect:
        artifact Matchers.strictlyEqual(equalArtifact)
        artifact != differentModule
        artifact != differentName
    }

    def dep(String group, String moduleName, String version) {
        ResolvedModuleVersion module = Mock()
        _ * module.id >> new DefaultModuleVersionIdentifier(group, moduleName, version)
        module
    }
}
