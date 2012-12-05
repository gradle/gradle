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

import org.apache.ivy.core.module.descriptor.DefaultArtifact
import org.apache.ivy.core.module.id.ModuleRevisionId
import spock.lang.Specification

class M2ResourcePatternTest extends Specification {
    def "substitutes artifact attributes into pattern"() {
        def pattern = new M2ResourcePattern("prefix/[organisation]/[module]/[revision]/[type]s/[revision]/[artifact].[ext]")
        def artifact1 = DefaultArtifact.newIvyArtifact(ModuleRevisionId.newInstance("group", "projectA", "1.2"), new Date())
        def artifact2 = DefaultArtifact.newIvyArtifact(ModuleRevisionId.newInstance("org.group", "projectA", "1.2"), new Date())
        def artifact3 = DefaultArtifact.newIvyArtifact(ModuleRevisionId.newInstance(null, "projectA", "1.2"), new Date())

        expect:
        pattern.toPath(artifact1) == 'prefix/group/projectA/1.2/ivys/1.2/ivy.xml'
        pattern.toPath(artifact2) == 'prefix/org/group/projectA/1.2/ivys/1.2/ivy.xml'
        pattern.toPath(artifact3) == 'prefix/[organisation]/projectA/1.2/ivys/1.2/ivy.xml'
    }

    def "substitutes artifact attributes without version into pattern"() {
        def pattern = new M2ResourcePattern("prefix/[organisation]/[module]/[revision]/[type]s/[revision]/[artifact].[ext]")
        def artifact1 = DefaultArtifact.newIvyArtifact(ModuleRevisionId.newInstance("group", "projectA", "1.2"), new Date())
        def artifact2 = DefaultArtifact.newIvyArtifact(ModuleRevisionId.newInstance("org.group", "projectA", "1.2"), new Date())
        def artifact3 = DefaultArtifact.newIvyArtifact(ModuleRevisionId.newInstance(null, "projectA", "1.2"), new Date())

        expect:
        pattern.toPathWithoutRevision(artifact1) == 'prefix/group/projectA/[revision]/ivys/[revision]/ivy.xml'
        pattern.toPathWithoutRevision(artifact2) == 'prefix/org/group/projectA/[revision]/ivys/[revision]/ivy.xml'
        pattern.toPathWithoutRevision(artifact3) == 'prefix/[organisation]/projectA/[revision]/ivys/[revision]/ivy.xml'
    }

    def "can build module path"() {
        def pattern = new M2ResourcePattern("prefix/" + MavenPattern.M2_PATTERN)
        def artifact1 = DefaultArtifact.newIvyArtifact(ModuleRevisionId.newInstance("group", "projectA", "1.2"), new Date())
        def artifact2 = DefaultArtifact.newIvyArtifact(ModuleRevisionId.newInstance("org.group", "projectA", "1.2"), new Date())

        expect:
        pattern.toModulePath(artifact1) == 'prefix/group/projectA'
        pattern.toModulePath(artifact2) == 'prefix/org/group/projectA'
    }

    def "can build module version path"() {
        def pattern = new M2ResourcePattern("prefix/" + MavenPattern.M2_PATTERN)
        def artifact1 = DefaultArtifact.newIvyArtifact(ModuleRevisionId.newInstance("group", "projectA", "1.2"), new Date())
        def artifact2 = DefaultArtifact.newIvyArtifact(ModuleRevisionId.newInstance("org.group", "projectA", "1.2"), new Date())

        expect:
        pattern.toModuleVersionPath(artifact1) == 'prefix/group/projectA/1.2'
        pattern.toModuleVersionPath(artifact2) == 'prefix/org/group/projectA/1.2'
    }

    def "can substitute revision in filename by timestamped version when provided"() {
        def pattern = new M2ResourcePattern("prefix/" + MavenPattern.M2_PATTERN)
        def artifact1 = new DefaultArtifact(ModuleRevisionId.newInstance("group", "projectA", "1.2-SNAPSHOT"), new Date(), "projectA", "ivy", "ivy", [timestamp: "1.2-20100101.120001-1"])
        def artifact2 = DefaultArtifact.newIvyArtifact(ModuleRevisionId.newInstance("org.group", "projectA", "1.2-SNAPSHOT"), new Date())
        expect:
        pattern.toPath(artifact1) == 'prefix/group/projectA/1.2-SNAPSHOT/projectA-1.2-20100101.120001-1.ivy'
        pattern.toPath(artifact2) == 'prefix/org/group/projectA/1.2-SNAPSHOT/ivy-1.2-SNAPSHOT.xml'
    }

    def "throws UnsupportedOperationException for non M2 compatible pattern"() {
        def pattern = new M2ResourcePattern("/non/m2/pattern")

        when:
        pattern.toModulePath(DefaultArtifact.newIvyArtifact(ModuleRevisionId.newInstance("group", "projectA", "1.2"), new Date()))

        then:
        thrown(UnsupportedOperationException)
    }
}
