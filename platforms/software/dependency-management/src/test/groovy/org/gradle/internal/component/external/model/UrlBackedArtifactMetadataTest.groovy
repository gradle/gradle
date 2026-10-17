/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.internal.component.external.model

import org.gradle.api.internal.artifacts.DefaultModuleIdentifier
import org.gradle.api.internal.artifacts.repositories.resolver.MavenUniqueSnapshotComponentIdentifier
import org.gradle.util.Matchers
import spock.lang.Issue
import spock.lang.Specification

class UrlBackedArtifactMetadataTest extends Specification {
    def componentId = DefaultModuleComponentIdentifier.newId(DefaultModuleIdentifier.newId("group", "module"), "1.0")

    def "normalizes '#relativeUrl' to '#filePath'"() {
        expect:
        UrlBackedArtifactMetadata.normalizePath(relativeUrl) == filePath

        where:
        relativeUrl            | filePath
        "module-1.0.jar"       | "module-1.0.jar"
        "./module-1.0.jar"     | "module-1.0.jar"
        "./a//b.jar"           | "a/b.jar"
        "a/./b.jar"            | "a/b.jar"
        "a/../b.jar"           | "b.jar"
        "../x.jar"             | "../x.jar"
        "a/../../b.jar"        | "../b.jar"
        "../../a/../b.jar"     | "../../b.jar"
        "a/b/"                 | "a/b"
        "/a/b.jar"             | "/a/b.jar"
        "/./a/../b.jar"        | "/b.jar"
        "a%20b.jar"            | "a%20b.jar"
    }

    def "identifier uses the destination file name and the normalized url"() {
        when:
        def id = new UrlBackedArtifactMetadata(componentId, "module.jar", "./dir/module-1.0.jar").id

        then:
        id instanceof ModuleComponentFileArtifactIdentifier
        id.destinationFileName == "module.jar"
        id.filePath == "dir/module-1.0.jar"
    }

    def "identifiers are equal when urls point to the same file"() {
        def id = new UrlBackedArtifactMetadata(componentId, "module-1.0.jar", "module-1.0.jar").id
        def sameFile = new UrlBackedArtifactMetadata(componentId, "module-1.0.jar", "./dir/../module-1.0.jar").id
        def differentDir = new UrlBackedArtifactMetadata(componentId, "module-1.0.jar", "dir/module-1.0.jar").id

        expect:
        id Matchers.strictlyEqual(sameFile)
        id != differentDir
    }

    def "identifier is equal to the identifier of a matching ad hoc artifact"() {
        def id = new UrlBackedArtifactMetadata(componentId, "module-1.0-sources.jar", "module-1.0-sources.jar").id
        def adhoc = new DefaultModuleComponentArtifactIdentifier(componentId, "module", "jar", "jar", "sources")

        expect:
        id Matchers.strictlyEqual(adhoc)
    }

    @Issue("https://github.com/gradle/gradle/issues/38831")
    def "identifiers with the same url but different destination file names are not equal"() {
        def id = new UrlBackedArtifactMetadata(componentId, "libmodule.so", "module-1.0-debug.so").id
        def otherName = new UrlBackedArtifactMetadata(componentId, "module-debug.so", "module-1.0-debug.so").id
        def adhoc = new DefaultModuleComponentArtifactIdentifier(componentId, "module", "so", "so", "debug")

        expect:
        id.filePath == adhoc.filePath
        id != otherName
        id != adhoc
    }

    def "identifier for a unique snapshot is equal to the identifier of a matching ad hoc artifact"() {
        def snapshotId = new MavenUniqueSnapshotComponentIdentifier(DefaultModuleIdentifier.newId("group", "module"), "1.0-SNAPSHOT", "20260101.120000-1")
        def id = new UrlBackedArtifactMetadata(snapshotId, "module-1.0-20260101.120000-1-sources.jar", "module-1.0-20260101.120000-1-sources.jar").id
        def adhoc = new DefaultModuleComponentArtifactIdentifier(snapshotId, "module", "jar", "jar", "sources")

        expect:
        id instanceof DefaultModuleComponentArtifactIdentifier
        id Matchers.strictlyEqual(adhoc)
    }
}
