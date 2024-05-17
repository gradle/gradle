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
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactIdentifier
import org.gradle.internal.component.external.model.DefaultModuleComponentArtifactMetadata
import org.gradle.internal.component.external.model.DefaultModuleComponentIdentifier
import org.gradle.internal.component.external.model.ModuleComponentArtifactMetadata
import org.gradle.internal.component.model.DefaultIvyArtifactName
import spock.lang.Specification

class IvyResourcePatternTest extends Specification {
    def "can construct from URI and pattern"() {
        expect:
        new IvyResourcePattern(new URI(uri), pattern).pattern == expectedPattern

        where:
        uri                | pattern            | expectedPattern
        "http://host/"     | "a/[revision]/c"   | "http://host/a/[revision]/c"
        "http://host"      | "a/b/c"            | "http://host/a/b/c"
        "http://host/"     | "/a/b/c"           | "http://host/a/b/c"
        "http://host/"     | ""                 | "http://host/"
        "http://host"      | ""                 | "http://host"
        "http://host/"     | "/"                | "http://host/"
        "http://host/repo" | "query?[revision]" | "http://host/repo/query?[revision]"
    }

    def "substitutes artifact attributes into pattern"() {
        def pattern = new IvyResourcePattern("prefix/[organisation]-[module]/[revision]/[type]s/[revision]/[artifact].[ext]")
        def artifact = artifact(group, module, version)

        expect:
        pattern.getLocation(artifact).path == expectedPath

        where:
        group       | module     | version | expectedPath
        "group"     | "projectA" | "1.2"   | 'prefix/group-projectA/1.2/ivys/1.2/ivy.xml'
        "org.group" | "projectA" | "1.2"   | 'prefix/org.group-projectA/1.2/ivys/1.2/ivy.xml'
        "##??::"    | "projectA" | "1.2"   | 'prefix/##??::-projectA/1.2/ivys/1.2/ivy.xml'
    }

    def "determines artifact location by substituting artifact attributes into pattern and resolving relative to base URI"() {
        def pattern = new IvyResourcePattern(new URI('http://server/repo'), "lookup/[organisation]-[module]/[revision]/[type]s/[revision]/[artifact].[ext]")
        def artifact = artifact(group, module, version)

        expect:
        pattern.getLocation(artifact).uri == new URI(expectedUri)

        where:
        group       | module     | version | expectedUri
        "group"     | "projectA" | "1.2"   | 'http://server/repo/lookup/group-projectA/1.2/ivys/1.2/ivy.xml'
        "org.group" | "projectA" | "1.2"   | 'http://server/repo/lookup/org.group-projectA/1.2/ivys/1.2/ivy.xml'
        "#?: %12"   | "projectA" | "1.2"   | 'http://server/repo/lookup/%23%3F:%20%2512-projectA/1.2/ivys/1.2/ivy.xml'
    }

    def "substitutes attributes into pattern to determine version list pattern"() {
        def pattern = new IvyResourcePattern("prefix/[organisation]-[module]/[revision]/[type]s/[revision]/[artifact].[ext]")
        def ivyName = new DefaultIvyArtifactName("ivy", "ivy", "xml")
        def moduleId = new DefaultModuleIdentifier(group, module)

        expect:
        pattern.toVersionListPattern(moduleId, ivyName).path == expectedPath

        where:
        group       | module     | expectedPath
        "group"     | "projectA" | 'prefix/group-projectA/[revision]/ivys/[revision]/ivy.xml'
        "org.group" | "projectA" | 'prefix/org.group-projectA/[revision]/ivys/[revision]/ivy.xml'
    }

    def "computes module version path"() {
        def ivyPattern = new IvyResourcePattern(pattern)
        def moduleId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(group, module), version)

        expect:
        ivyPattern.toModuleVersionPath(moduleId).path == expectedPath

        where:
        group        | module | version | pattern                                                                                  | expectedPath
        'org.gradle' | 'test' | '1.0'   | 'prefix/[organisation]/[module]/[revision]/[artifact]-[revision](-[classifier])(.[ext])' | 'prefix/org.gradle/test/1.0'
        'org.gradle' | 'test' | '1.1'   | 'prefix/[organisation]/[module]/[revision]/[artifact]-[revision]-artifact.bin'           | 'prefix/org.gradle/test/1.1'
        'org.gradle' | 'test' | '1.2'   | 'repo/[organisation]-[module]-[revision]/[artifact]-[revision]-artifact.bin'             | 'repo/org.gradle-test-1.2'
    }

    def "cannot compute module version path if pattern doesn't end with /[artifact]"() {
        def ivyPattern = new IvyResourcePattern(pattern)
        def moduleId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(group, module), version)

        when:
        ivyPattern.toModuleVersionPath(moduleId).path

        then:
        UnsupportedOperationException e = thrown()
        e.message == 'Cannot locate module version for non standard Ivy layout.'

        where:
        group        | module | version | pattern
        'org.gradle' | 'test' | '1.0'   | 'prefix/[organisation]/[module]/[revision]/foo'
        'org.gradle' | 'test' | '1.0'   | 'prefix/[organisation]/[module]/[revision]/[revision]'
        'org.gradle' | 'test' | '1.0'   | 'prefix/[organisation]/[module]/[artifact]/[revision]'
    }

    private static ModuleComponentArtifactMetadata artifact(String group, String name, String version) {
        final componentIdentifier = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId(group, name), version)
        return new DefaultModuleComponentArtifactMetadata(new DefaultModuleComponentArtifactIdentifier(componentIdentifier, "ivy", "ivy", "xml"))
    }
}
