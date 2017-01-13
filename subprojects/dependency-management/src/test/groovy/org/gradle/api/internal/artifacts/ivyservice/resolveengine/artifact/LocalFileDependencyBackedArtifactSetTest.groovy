/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.artifacts.ivyservice.resolveengine.artifact

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.file.FileCollection
import org.gradle.internal.component.local.model.LocalFileDependencyMetadata
import spock.lang.Specification

class LocalFileDependencyBackedArtifactSetTest extends Specification {
    def dep = Mock(LocalFileDependencyMetadata)
    def set = new LocalFileDependencyBackedArtifactSet(dep)

    def "has no artifacts"() {
        expect:
        set.artifacts.empty
    }

    def "visits files when visitor requests them"() {
        def visitor = Mock(ArtifactVisitor)
        def id = Stub(ComponentIdentifier)
        def files = Stub(FileCollection)

        when:
        set.visit(visitor)

        then:
        _ * dep.componentId >> id
        _ * dep.files >> files
        1 * visitor.includeFiles() >> true
        1 * visitor.visitFiles(id, files)
        0 * visitor._

        when:
        set.visit(visitor)

        then:
        1 * visitor.includeFiles() >> false
        0 * visitor._
    }
}
