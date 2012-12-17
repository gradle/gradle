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

import org.gradle.api.artifacts.repositories.PasswordCredentials
import org.gradle.test.fixtures.server.http.HttpServer
import org.gradle.util.TestFile

class MavenHttpModule implements MavenModule {
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
        return new MavenHttpArtifact(server, "${moduleRootPath}/${backingModule.version}", this, options)
    }

    /**
     * Adds an additional artifact to this module.
     * @param options Can specify any of: type or classifier
     */
    HttpArtifact artifact(Map<String, ?> options) {
        backingModule.artifact(options)
        return new MavenHttpArtifact(server, "${moduleRootPath}/${backingModule.version}", this, options)
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

    MavenModule parent(String group, String artifactId, String version) {
        backingModule.parent(group, artifactId, version)
        return this
    }

    MavenHttpModule dependsOn(String group, String artifactId, String version) {
        backingModule.dependsOn(group, artifactId, version)
        return this
    }

    MavenModule hasPackaging(String packaging) {
        backingModule.hasPackaging(packaging)
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

    PomHttpResource getPom() {
        return new PomHttpResource(server, getPomPath(), backingModule)
    }

    MavenMetaData getRootMetaData() {
        backingModule.rootMetaData
    }

    TestFile getRootMetaDataFile() {
        return backingModule.rootMetaDataFile
    }

    void allowAll() {
        server.allowGetOrHead(moduleVersionPath, backingModule.moduleDir)
    }

    void expectMetaDataGet() {
        server.expectGet(getMetaDataPath(), metaDataFile)
    }

    String getRootMetaDataPath() {
        "$moduleRootPath/$rootMetaDataFile.name"
    }

    String getMetaDataPath() {
        "$moduleVersionPath/$metaDataFile.name"
    }

    TestFile sha1File(TestFile file) {
        backingModule.getSha1File(file)
    }

    String sha1Path(String path) {
        "${path}.sha1"
    }

    TestFile md5File(TestFile file) {
        backingModule.getMd5File(file)
    }

    String md5Path(String path) {
        "${path}.md5"
    }

    void expectMetaDataGetMissing() {
        server.expectGetMissing(metaDataPath)
    }

    void expectRootMetaDataGetMissing(PasswordCredentials passwordCredentials = null) {
        server.expectGetMissing(rootMetaDataPath, passwordCredentials)
    }

    void expectMetaDataPut(Integer statusCode = 200, PasswordCredentials credentials = null) {
        server.expectPut(metaDataPath, backingModule.metaDataFile, statusCode, credentials)
    }

    void expectMetaDataSha1Put(Integer statusCode = 200, PasswordCredentials credentials = null) {
        server.expectPut(sha1Path(metaDataPath), sha1File(metaDataFile), statusCode, credentials)
    }

    void expectMetaDataMd5Put(Integer statusCode = 200, PasswordCredentials credentials = null) {
        server.expectPut(md5Path(metaDataPath), md5File(metaDataFile), statusCode, credentials)
    }

    void expectRootMetaDataPut(PasswordCredentials credentials) {
        expectRootMetaDataPut(200, credentials)
    }

    void expectRootMetaDataPut(Integer statusCode = 200, PasswordCredentials credentials = null) {
        server.expectPut(rootMetaDataPath, rootMetaDataFile, statusCode, credentials)
    }

    void expectRootMetaDataSha1Put(PasswordCredentials credentials) {
        expectRootMetaDataSha1Put(200, credentials)
    }

    void expectRootMetaDataSha1Put(Integer statusCode = 200, PasswordCredentials credentials = null) {
        server.expectPut(sha1Path(rootMetaDataPath), sha1File(rootMetaDataFile), statusCode, credentials)
    }

    void expectRootMetaDataMd5Put(PasswordCredentials credentials) {
        expectRootMetaDataMd5Put(200, credentials)
    }

    void expectRootMetaDataMd5Put(Integer statusCode = 200, PasswordCredentials credentials = null) {
        server.expectPut(md5Path(rootMetaDataPath), md5File(rootMetaDataFile), statusCode, credentials)
    }

    String getPomPath() {
        "$moduleVersionPath/$pomFile.name"
    }

    void verifyArtifactChecksums() {
        backingModule.verifyChecksums(artifactFile)
    }

    void verifyPomChecksums() {
        backingModule.verifyChecksums(pomFile)
    }

    void verifyRootMetaDataChecksums() {
        backingModule.verifyChecksums(rootMetaDataFile)
    }
}
