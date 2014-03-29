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



package org.gradle.test.fixtures.maven

import org.gradle.test.fixtures.HttpModule
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.HttpServer

class MavenHttpModule implements MavenModule, HttpModule {
    private final HttpServer server
    private final String moduleRootPath
    private final MavenFileModule backingModule

    MavenHttpModule(HttpServer server, String repoRoot, MavenFileModule backingModule) {
        this.backingModule = backingModule
        this.server = server
        this.moduleRootPath = "${repoRoot}/${backingModule.groupId.replace('.', '/')}/${backingModule.artifactId}"
    }

    protected String getModuleVersionPath() {
        "${moduleRootPath}/${backingModule.version}"
    }

    HttpArtifact getArtifact(Map options = [:]) {
        return new MavenHttpArtifact(server, "${moduleRootPath}/${backingModule.version}", backingModule, options)
    }

    /**
     * Adds an additional artifact to this module.
     * @param options Can specify any of: type or classifier
     */
    HttpArtifact artifact(Map<String, ?> options = [:]) {
        backingModule.artifact(options)
        return new MavenHttpArtifact(server, "${moduleRootPath}/${backingModule.version}", backingModule, options)
    }

    MavenHttpModule withSourceAndJavadoc() {
        artifact(classifier: "sources")
        artifact(classifier: "javadoc")
        return this
    }

    MavenHttpModule publish() {
        backingModule.publish()
        return this
    }

    MavenHttpModule publishPom() {
        backingModule.publishPom()
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

    MavenHttpModule parent(String group, String artifactId, String version) {
        backingModule.parent(group, artifactId, version)
        return this
    }

    MavenHttpModule dependsOn(String group, String artifactId, String version) {
        backingModule.dependsOn(group, artifactId, version)
        return this
    }

    MavenHttpModule dependsOn(String group, String artifactId, String version, String type) {
        backingModule.dependsOn(group, artifactId, version, type)
        return this
    }

    MavenHttpModule hasPackaging(String packaging) {
        backingModule.hasPackaging(packaging)
        return this
    }

    MavenHttpModule hasType(String type) {
        backingModule.hasType(type)
        return this
    }

    TestFile getPomFile() {
        return backingModule.pomFile
    }

    TestFile getArtifactFile(Map options = [:]) {
        return backingModule.getArtifactFile(options)
    }

    TestFile getMetaDataFile() {
        return backingModule.metaDataFile
    }

    MavenPom getParsedPom() {
        return backingModule.parsedPom;
    }

    PomHttpArtifact getPom() {
        return new PomHttpArtifact(server, getModuleVersionPath(), backingModule)
    }

    MetaDataArtifact getRootMetaData() {
        return new MetaDataArtifact(server, "$moduleRootPath", backingModule)
    }

    TestFile getRootMetaDataFile() {
        return backingModule.rootMetaDataFile
    }

    void allowAll() {
        server.allowGetOrHead(moduleVersionPath, backingModule.moduleDir)
    }

    HttpResource getMetaData() {
        return new BasicHttpResource(server, metaDataFile, getMetaDataPath())
    }

    String getMetaDataPath() {
        "$moduleVersionPath/$metaDataFile.name"
    }
}
