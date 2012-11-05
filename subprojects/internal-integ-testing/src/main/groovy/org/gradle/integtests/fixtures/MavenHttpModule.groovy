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

package org.gradle.integtests.fixtures

import org.gradle.test.fixtures.server.HttpServer
import org.gradle.util.TestFile

class MavenHttpModule implements MavenModule {
    private final HttpServer server
    private final String modulePath
    private final MavenFileModule backingModule

    MavenHttpModule(HttpServer server, String modulePath, MavenFileModule backingModule) {
        this.backingModule = backingModule
        this.server = server
        this.modulePath = modulePath
    }

    MavenHttpModule publish() {
        backingModule.publish()
        return this
    }

    MavenHttpModule publishWithChangedContent() {
        backingModule.publishWithChangedContent()
        return this
    }

    MavenHttpModule withNonUniqueSnapshots() {
        backingModule.withNonUniqueSnapshots()
        return this
    }

    MavenModule dependsOn(String group, String artifactId, String version) {
        backingModule.dependsOn(group, artifactId, version)
        return this
    }

    TestFile getPomFile() {
        return backingModule.pomFile
    }

    TestFile getArtifactFile() {
        return backingModule.artifactFile
    }

    TestFile getMetaDataFile() {
        return backingModule.metaDataFile
    }

    void allowAll() {
        server.allowGetOrHead(modulePath, backingModule.moduleDir)
    }

    void expectMetaDataGet() {
        server.expectGet("$modulePath/$metaDataFile.name", metaDataFile)
    }

    void expectMetaDataGetMissing() {
        server.expectGetMissing("$modulePath/$metaDataFile.name")
    }

    void expectPomGet() {
        server.expectGet("$modulePath/$pomFile.name", pomFile)
    }

    void expectPomHead() {
        server.expectHead("$modulePath/$pomFile.name", pomFile)
    }

    void expectPomGetMissing() {
        server.expectGetMissing("$modulePath/$missingPomName")
    }

    void expectPomSha1Get() {
        server.expectGet("$modulePath/${pomFile.name}.sha1", backingModule.sha1File(pomFile))
    }

    void expectPomSha1GetMissing() {
        server.expectGetMissing("$modulePath/${missingPomName}.sha1")
    }

    private String getMissingPomName() {
        if (backingModule.version.endsWith("-SNAPSHOT")) {
            return "${backingModule.artifactId}-${backingModule.version}.pom"
        } else {
            return pomFile.name
        }
    }

    void expectArtifactGet() {
        server.expectGet("$modulePath/$artifactFile.name", artifactFile)
    }

    void expectArtifactHead() {
        server.expectHead("$modulePath/$artifactFile.name", artifactFile)
    }

    void expectArtifactGetMissing() {
        server.expectGetMissing("$modulePath/$missingArtifactName")
    }

    void expectArtifactHeadMissing() {
        server.expectHeadMissing("$modulePath/$missingArtifactName")
    }

    void expectArtifactSha1Get() {
        server.expectGet("$modulePath/${artifactFile.name}.sha1", backingModule.sha1File(artifactFile))
    }

    void expectArtifactSha1GetMissing() {
        server.expectGetMissing("$modulePath/${missingArtifactName}.sha1")
    }

    private String getMissingArtifactName() {
        if (backingModule.version.endsWith("-SNAPSHOT")) {
            return "${backingModule.artifactId}-${backingModule.version}.jar"
        } else {
            return artifactFile.name
        }
    }
}
