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

package org.gradle.api.internal.artifacts.repositories

import org.apache.ivy.core.module.descriptor.DefaultArtifact
import org.apache.ivy.core.module.id.ModuleRevisionId
import spock.lang.Specification

class IvyResourcePatternTest extends Specification {
    def "substitutes artifact attributes into pattern"() {
        def pattern = new IvyResourcePattern("prefix/[organisation]-[module]/[revision]/[type]s/[revision]/[artifact].[ext]")
        def artifact1 = DefaultArtifact.newIvyArtifact(ModuleRevisionId.newInstance("group", "projectA", "1.2"), new Date())
        def artifact2 = DefaultArtifact.newIvyArtifact(ModuleRevisionId.newInstance("org.group", "projectA", "1.2"), new Date())
        def artifact3 = DefaultArtifact.newIvyArtifact(ModuleRevisionId.newInstance(null, "projectA", "1.2"), new Date())

        expect:
        pattern.toPath(artifact1) == 'prefix/group-projectA/1.2/ivys/1.2/ivy.xml'
        pattern.toPath(artifact2) == 'prefix/org.group-projectA/1.2/ivys/1.2/ivy.xml'
        pattern.toPath(artifact3) == 'prefix/[organisation]-projectA/1.2/ivys/1.2/ivy.xml'
    }

    def "substitutes artifact attributes without revision into pattern"() {
        def pattern = new IvyResourcePattern("prefix/[organisation]-[module]/[revision]/[type]s/[revision]/[artifact].[ext]")
        def artifact1 = DefaultArtifact.newIvyArtifact(ModuleRevisionId.newInstance("group", "projectA", "1.2"), new Date())
        def artifact2 = DefaultArtifact.newIvyArtifact(ModuleRevisionId.newInstance("org.group", "projectA", "1.2"), new Date())

        expect:
        pattern.toPathWithoutRevision(artifact1) == 'prefix/group-projectA/[revision]/ivys/[revision]/ivy.xml'
        pattern.toPathWithoutRevision(artifact2) == 'prefix/org.group-projectA/[revision]/ivys/[revision]/ivy.xml'
    }
}
