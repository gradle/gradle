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
package org.gradle.test.fixtures.server.http

import org.gradle.test.fixtures.HttpModule
import org.gradle.test.fixtures.maven.DelegatingMavenModule
import org.gradle.test.fixtures.maven.MavenFileModule
import org.gradle.test.fixtures.maven.RemoteMavenModule

class MavenHttpModule extends DelegatingMavenModule<MavenHttpModule> implements RemoteMavenModule, HttpModule {
    private final HttpServer server
    private final String repoRoot
    private final String moduleRootUriPath
    private final String uriPath
    private final MavenFileModule backingModule

    MavenHttpModule(HttpServer server, String repoRoot, MavenFileModule backingModule) {
        super(backingModule)
        this.backingModule = backingModule
        this.server = server
        this.repoRoot = repoRoot
        this.moduleRootUriPath = "${repoRoot}/${backingModule.moduleRootPath}"
        this.uriPath = "${repoRoot}/${backingModule.path}"
    }

    HttpArtifact getArtifact(Map options = [:]) {
        return new MavenHttpArtifact(server, repoRoot, backingModule, backingModule.getArtifact(options))
    }

    @Override
    HttpArtifact getArtifact(String relativePath) {
        return new MavenHttpArtifact(server, repoRoot, backingModule, backingModule.getArtifact(relativePath))
    }

    /**
     * Adds an additional artifact to this module.
     * @param options Can specify any of: type or classifier
     */
    HttpArtifact artifact(Map<String, ?> options = [:]) {
        backingModule.artifact(options)
        return getArtifact(options)
    }

    MavenHttpModule withSourceAndJavadoc() {
        artifact(classifier: "sources")
        artifact(classifier: "javadoc")
        return this
    }

    MavenHttpModule withNoPom() {
        backingModule.withNoPom()
        return this
    }

    PomHttpArtifact getPom() {
        return new PomHttpArtifact(server, uriPath, backingModule)
    }

    @Override
    HttpArtifact getModuleMetadata() {
        return getArtifact(type: 'module')
    }

    MetaDataArtifact getRootMetaData() {
        return new MetaDataArtifact(server, moduleRootUriPath, backingModule)
    }

    MavenHttpModule allowAll() {
        server.allowGetOrHead(uriPath, backingModule.moduleDir)
        return this
    }

    MavenHttpModule revalidate() {
        server.allowGetOrHeadMissing(pomPath)
        server.allowGetOrHeadMissing(metaDataPath)
        server.allowGetOrHeadWithRevalidate(artifactPath, artifactFile)
        return this
    }

    void missing() {
        server.allowGetOrHeadMissing(pomPath)
        server.allowGetOrHeadMissing(metaDataPath)
        server.allowGetOrHeadMissing(artifactPath)
    }

    HttpResource getMetaData() {
        return new BasicHttpResource(server, metaDataFile, getMetaDataPath())
    }

    String getMetaDataPath() {
        return "$uriPath/$metaDataFile.name"
    }

    String getArtifactPath() {
        return "$uriPath/$artifactFile.name"
    }

    String getPomPath() {
        return "$uriPath/$pomFile.name"
    }
}
