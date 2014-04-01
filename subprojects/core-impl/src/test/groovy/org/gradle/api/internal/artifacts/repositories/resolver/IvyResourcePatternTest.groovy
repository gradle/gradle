/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.artifacts.repositories.resolver
import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultModuleVersionIdentifier
import org.gradle.api.internal.artifacts.metadata.DefaultIvyArtifactName
import org.gradle.api.internal.artifacts.metadata.DefaultModuleVersionArtifactIdentifier
import org.gradle.api.internal.artifacts.metadata.DefaultModuleVersionArtifactMetaData
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData
import spock.lang.Specification

class IvyResourcePatternTest extends Specification {

    def "substitutes artifact attributes into pattern"() {
        def pattern = new IvyResourcePattern("prefix/[organisation]-[module]/[revision]/[type]s/[revision]/[artifact].[ext]")
        def artifact1 = artifact("group", "projectA", "1.2")
        def artifact2 = artifact("org.group", "projectA", "1.2")

        expect:
        pattern.toPath(artifact1) == 'prefix/group-projectA/1.2/ivys/1.2/ivy.xml'
        pattern.toPath(artifact2) == 'prefix/org.group-projectA/1.2/ivys/1.2/ivy.xml'
    }

    def "substitutes artifact attributes without revision into pattern"() {
        def pattern = new IvyResourcePattern("prefix/[organisation]-[module]/[revision]/[type]s/[revision]/[artifact].[ext]")
        def ivyName = new DefaultIvyArtifactName("ivy", "ivy", "xml")
        def module1 = new DefaultModuleIdentifier("group", "projectA")
        def module2 = new DefaultModuleIdentifier("org.group", "projectA")

        expect:
        pattern.toVersionListPattern(module1, ivyName) == 'prefix/group-projectA/[revision]/ivys/[revision]/ivy.xml'
        pattern.toVersionListPattern(module2, ivyName) == 'prefix/org.group-projectA/[revision]/ivys/[revision]/ivy.xml'
    }

    private static ModuleVersionArtifactMetaData artifact(String group, String name, String version) {
        final moduleVersionId = DefaultModuleVersionIdentifier.newId(group, name, version)
        return new DefaultModuleVersionArtifactMetaData(new DefaultModuleVersionArtifactIdentifier(moduleVersionId, "ivy", "ivy", "xml"))
    }
}
