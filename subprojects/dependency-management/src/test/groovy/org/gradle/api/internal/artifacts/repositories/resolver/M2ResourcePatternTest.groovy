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
import org.gradle.internal.component.model.DefaultIvyArtifactName
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import spock.lang.Specification

import static org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier.newId

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
        "http://host"  | ""       | "http://host"
    }

    def "substitutes artifact attributes into pattern"() {
        def pattern = new M2ResourcePattern("prefix/[organisation]/[module]/[revision]/[type]s/[revision]/[artifact].[ext]")
        def artifact = artifact(group, module, version)

        expect:
        pattern.getLocation(artifact).path == expectPath

        where:
        group       | module     | version | expectPath
        "group"     | "projectA" | "1.2"   | 'prefix/group/projectA/1.2/ivys/1.2/ivy.xml'
        "org.group" | "projectA" | "1.2"   | 'prefix/org/group/projectA/1.2/ivys/1.2/ivy.xml'
        "##??::"    | "projectA" | "1.2"   | 'prefix/##??::/projectA/1.2/ivys/1.2/ivy.xml'
    }

    def "substitutes snapshot artifact attributes into pattern"() {
        def pattern = new M2ResourcePattern("prefix/" + MavenPattern.M2_PATTERN)
        def snapshotId = new MavenUniqueSnapshotComponentIdentifier(DefaultModuleIdentifier.newId("group", "projectA"), "1.2-SNAPSHOT", "2014-timestamp-3333")

        def artifact1 = new DefaultModuleComponentArtifactMetadata(new DefaultModuleComponentArtifactIdentifier(snapshotId, "projectA", "pom", "pom"))

        expect:
        pattern.getLocation(artifact1).path == 'prefix/group/projectA/1.2-SNAPSHOT/projectA-1.2-2014-timestamp-3333.pom'
    }

    def "determines artifact location by substituting artifact attributes into pattern and resolving relative to base URI"() {
        def pattern = new M2ResourcePattern(new URI("http://server/lookup"), "[organisation]/[module]/[revision]/[type]s/[revision]/[artifact].[ext]")
        def artifact = artifact(group, module, version)

        expect:
        pattern.getLocation(artifact).uri == new URI(expectPath)

        where:
        group       | module     | version | expectPath
        "group"     | "projectA" | "1.2"   | 'http://server/lookup/group/projectA/1.2/ivys/1.2/ivy.xml'
        "org.group" | "projectA" | "1.2"   | 'http://server/lookup/org/group/projectA/1.2/ivys/1.2/ivy.xml'
        "#?:%12"    | "projectA" | "1.2"   | 'http://server/lookup/%23%3F:%2512/projectA/1.2/ivys/1.2/ivy.xml'
    }

    def "substitutes attributes into pattern to determine version list pattern"() {
        def pattern = new M2ResourcePattern("prefix/[organisation]/[module]/[revision]/[type]s/[revision]/[artifact].[ext]")
        def ivyName = new DefaultIvyArtifactName("projectA", "pom", "pom")
        def moduleId = DefaultModuleIdentifier.newId(group, module)

        expect:
        pattern.toVersionListPattern(moduleId, ivyName).path == expectedPath

        where:
        group       | module     | expectedPath
        "group"     | "projectA" | 'prefix/group/projectA/[revision]/poms/[revision]/projectA.pom'
        "org.group" | "projectA" | 'prefix/org/group/projectA/[revision]/poms/[revision]/projectA.pom'
    }

    def "can build module path"() {
        def pattern = new M2ResourcePattern("prefix/" + MavenPattern.M2_PATTERN)
        def module1 = DefaultModuleIdentifier.newId("group", "projectA")
        def module2 = DefaultModuleIdentifier.newId("org.group", "projectA")

        expect:
        pattern.toModulePath(module1).path == 'prefix/group/projectA'
        pattern.toModulePath(module2).path == 'prefix/org/group/projectA'
    }

    def "can build module version path"() {
        def pattern = new M2ResourcePattern("prefix/" + MavenPattern.M2_PATTERN)
        def component1 = newId(DefaultModuleIdentifier.newId("group", "projectA"), "1.2")
        def component2 = newId(DefaultModuleIdentifier.newId("org.group", "projectA"), "1.2")

        expect:
        pattern.toModuleVersionPath(component1).path == 'prefix/group/projectA/1.2'
        pattern.toModuleVersionPath(component2).path == 'prefix/org/group/projectA/1.2'
    }

    def "throws UnsupportedOperationException for non M2 compatible pattern"() {
        def pattern = new M2ResourcePattern("/non/m2/pattern")

        when:
        pattern.toModulePath(DefaultModuleIdentifier.newId("group", "module"))

        then:
        thrown(UnsupportedOperationException)
    }

    private static ModuleComponentArtifactMetadata artifact(String group, String name, String version) {
        final moduleVersionId = newId(DefaultModuleIdentifier.newId(group, name), version)
        return new DefaultModuleComponentArtifactMetadata(new DefaultModuleComponentArtifactIdentifier(moduleVersionId, "ivy", "ivy", "xml"))
    }
}
