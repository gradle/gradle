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

import org.gradle.util.TestFile

class MavenHttpModule {
    private final HttpServer server
    private final String modulePath
    private final MavenModule backingModule

    MavenHttpModule(HttpServer server, String modulePath, MavenModule backingModule) {
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

    TestFile getPomFile() {
        return backingModule.pomFile
    }

    TestFile getArtifactFile() {
        return backingModule.artifactFile
    }

    void allowAll() {
        server.allowGetOrHead(modulePath, backingModule.moduleDir)
    }

    void expectPomGet() {
        server.expectGet("$modulePath/$pomFile.name", pomFile)
    }

    void expectPomHead() {
        server.expectHead("$modulePath/$pomFile.name", pomFile)
    }

    void expectPomSha1Get() {
        server.expectGet("$modulePath/${pomFile.name}.sha1", backingModule.sha1File(pomFile))
    }

    void expectPomGetMissing() {
        server.expectGetMissing("$modulePath/$pomFile.name")
    }

    void expectArtifactGet() {
        server.expectGet("$modulePath/$artifactFile.name", artifactFile)
    }

    void expectArtifactHead() {
        server.expectHead("$modulePath/$artifactFile.name", artifactFile)
    }

    void expectArtifactSha1Get() {
        server.expectGet("$modulePath/${artifactFile.name}.sha1", backingModule.sha1File(artifactFile))
    }

    void expectArtifactGetMissing() {
        server.expectGetMissing("$modulePath/$artifactFile.name")
    }

    void expectArtifactHeadMissing() {
        server.expectHeadMissing("$modulePath/$artifactFile.name")
    }
}
