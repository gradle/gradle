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
import org.gradle.api.internal.artifacts.metadata.DefaultIvyArtifactName
import org.gradle.api.internal.artifacts.metadata.DefaultModuleVersionArtifactIdentifier
import org.gradle.api.internal.artifacts.metadata.DefaultModuleVersionArtifactMetaData
import org.gradle.api.internal.artifacts.metadata.ModuleVersionArtifactMetaData
import spock.lang.Specification

import static org.gradle.api.internal.artifacts.component.DefaultModuleComponentIdentifier.newId

class M2ResourcePatternTest extends Specification {
    def "can construct from URI and pattern"() {
        expect:
        new M2ResourcePattern(new URI(uri), pattern).pattern == expectedPattern

        where:
        uri            | pattern  | expectedPattern
        "http://host/" | "a/b/c"  | "http://host/a/b/c"
        "http://host"  | "a/b/c"  | "http://host/a/b/c"
        "http://host/" | "/a/b/c" | "http://host/a/b/c"
        "http://host/" | ""       | "http://host/"
        "http://host"  | ""       | "http://host/"
    }

    def "substitutes artifact attributes into pattern"() {
        def pattern = new M2ResourcePattern("prefix/[organisation]/[module]/[revision]/[type]s/[revision]/[artifact].[ext]")
        final String group = "group"
        final String name = "projectA"
        final String version = "1.2"
        def artifact1 = artifact(group, name, version)
        def artifact2 = artifact("org.group", "projectA", "1.2")

        expect:
        pattern.toPath(artifact1) == 'prefix/group/projectA/1.2/ivys/1.2/ivy.xml'
        pattern.toPath(artifact2) == 'prefix/org/group/projectA/1.2/ivys/1.2/ivy.xml'
    }

    def "substitutes snapshot artifact attributes into pattern"() {
        def pattern = new M2ResourcePattern("prefix/" + MavenPattern.M2_PATTERN)
        def snapshotId = new MavenUniqueSnapshotComponentIdentifier("group", "projectA", "1.2-SNAPSHOT", "2014-timestamp-3333")

        def artifact1 = new DefaultModuleVersionArtifactMetaData(new DefaultModuleVersionArtifactIdentifier(snapshotId, "projectA", "pom", "pom"))

        expect:
        pattern.toPath(artifact1) == 'prefix/group/projectA/1.2-SNAPSHOT/projectA-1.2-2014-timestamp-3333.pom'
    }

    def "substitutes module attributes into pattern to determine module pattern"() {
        def pattern = new M2ResourcePattern("prefix/[organisation]/[module]/[revision]/[type]s/[revision]/[artifact].[ext]")
        def ivyName = new DefaultIvyArtifactName("projectA", "pom", "pom")
        def module1 = new DefaultModuleIdentifier("group", "projectA")
        def module2 = new DefaultModuleIdentifier("org.group", "projectA")

        expect:
        pattern.toVersionListPattern(module1, ivyName) == 'prefix/group/projectA/[revision]/poms/[revision]/projectA.pom'
        pattern.toVersionListPattern(module2, ivyName) == 'prefix/org/group/projectA/[revision]/poms/[revision]/projectA.pom'
    }

    def "can build module path"() {
        def pattern = new M2ResourcePattern("prefix/" + MavenPattern.M2_PATTERN)
        def module1 = new DefaultModuleIdentifier("group", "projectA")
        def module2 = new DefaultModuleIdentifier("org.group", "projectA")

        expect:
        pattern.toModulePath(module1) == 'prefix/group/projectA'
        pattern.toModulePath(module2) == 'prefix/org/group/projectA'
    }

    def "can build module version path"() {
        def pattern = new M2ResourcePattern("prefix/" + MavenPattern.M2_PATTERN)
        def component1 = newId("group", "projectA", "1.2")
        def component2 = newId("org.group", "projectA", "1.2")

        expect:
        pattern.toModuleVersionPath(component1) == 'prefix/group/projectA/1.2'
        pattern.toModuleVersionPath(component2) == 'prefix/org/group/projectA/1.2'
    }

    def "throws UnsupportedOperationException for non M2 compatible pattern"() {
        def pattern = new M2ResourcePattern("/non/m2/pattern")

        when:
        pattern.toModulePath(new DefaultModuleIdentifier("group", "module"))

        then:
        thrown(UnsupportedOperationException)
    }

    private static ModuleVersionArtifactMetaData artifact(String group, String name, String version) {
        final moduleVersionId = newId(group, name, version)
        return new DefaultModuleVersionArtifactMetaData(new DefaultModuleVersionArtifactIdentifier(moduleVersionId, "ivy", "ivy", "xml"))
    }
}
