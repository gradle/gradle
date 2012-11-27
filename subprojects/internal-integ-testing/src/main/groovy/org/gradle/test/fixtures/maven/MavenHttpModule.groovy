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

    /**
     * Adds an additional artifact to this module.
     * @param options Can specify any of: type or classifier
     */
    MavenHttpModule artifact(Map<String, ?> options) {
        backingModule.artifact(options)
        return this
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

    TestFile getArtifactFile(Map options = [:]) {
        return backingModule.getArtifactFile(options)
    }

    TestFile getMetaDataFile() {
        return backingModule.metaDataFile
    }

    MavenPom getPom() {
        backingModule.pom
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

    void expectPomPut(PasswordCredentials credentials) {
        expectPomPut(200, credentials)
    }

    void expectPomPut(Integer statusCode = 200, PasswordCredentials credentials = null) {
        server.expectPut(pomPath, pomFile, statusCode, credentials)
    }

    void expectPomSha1Put(PasswordCredentials credentials) {
        expectPomSha1Put(200, credentials)
    }

    void expectPomSha1Put(Integer statusCode = 200, PasswordCredentials credentials = null) {
        server.expectPut(sha1Path(pomPath), sha1File(pomFile), statusCode, credentials)
    }

    void expectPomMd5Put(PasswordCredentials credentials) {
        expectPomMd5Put(200, credentials)
    }

    void expectPomMd5Put(Integer statusCode = 200, PasswordCredentials credentials = null) {
        server.expectPut(md5Path(pomPath), md5File(pomFile), statusCode, credentials)
    }

    void expectPomGet() {
        server.expectGet("$moduleVersionPath/$pomFile.name", pomFile)
    }

    void expectPomHead() {
        server.expectHead("$moduleVersionPath/$pomFile.name", pomFile)
    }

    void expectPomGetMissing() {
        server.expectGetMissing("$moduleVersionPath/$missingPomName")
    }

    void expectPomHeadMissing() {
        server.expectHeadMissing("$moduleVersionPath/$missingPomName")
    }

    void expectPomSha1Get() {
        server.expectGet("$moduleVersionPath/${pomFile.name}.sha1", backingModule.sha1File(pomFile))
    }

    void expectPomSha1GetMissing() {
        server.expectGetMissing("$moduleVersionPath/${missingPomName}.sha1")
    }

    private String getMissingPomName() {
        if (backingModule.version.endsWith("-SNAPSHOT")) {
            return "${backingModule.artifactId}-${backingModule.version}.pom"
        } else {
            return pomFile.name
        }
    }

    void expectArtifactGet(Map options) {
        server.expectGet(getArtifactPath(options), getArtifactFile(options))
    }

    void expectArtifactGet() {
        server.expectGet(getArtifactPath(), artifactFile)
    }

    String getArtifactPath(Map options = [:]) {
        "$moduleVersionPath/${getArtifactFile(options).name}"
    }

    String getPomPath() {
        "$moduleVersionPath/$pomFile.name"
    }

    void expectArtifactHead(Map options = [:]) {
        server.expectHead("$moduleVersionPath/${getArtifactFile(options).name}", getArtifactFile(options))
    }

    void expectArtifactGetMissing(Map options = [:]) {
        server.expectGetMissing("$moduleVersionPath/${getMissingArtifactName(options)}")
    }

    void expectArtifactHeadMissing(Map options=[:]) {
        server.expectHeadMissing("$moduleVersionPath/${getMissingArtifactName(options)}")
    }

    void expectArtifactSha1Get(Map options = [:]) {
        server.expectGet(getArtifactSha1Path(options), backingModule.sha1File(getArtifactFile(options)))
    }

    TestFile getArtifactSha1File() {
        backingModule.artifactSha1File
    }

    String getArtifactSha1Path(Map options = [:]) {
        "${getArtifactPath(options)}.sha1"
    }

    TestFile getArtifactMd5File() {
        backingModule.artifactMd5File
    }

    String getArtifactMd5Path() {
        "${artifactPath}.md5"
    }

    void expectArtifactSha1GetMissing() {
        server.expectGetMissing("$moduleVersionPath/${missingArtifactName}.sha1")
    }

    private String getMissingArtifactName(Map option = [:]) {
        if (backingModule.version.endsWith("-SNAPSHOT")) {
            return "${backingModule.artifactId}-${backingModule.version}${option["classifier"] ?"-"+option["classifier"]:""}.jar"
        } else {
            return artifactFile.name
        }
    }

    void expectArtifactPut(PasswordCredentials credentials) {
        expectArtifactPut(200, credentials)
    }

    void expectArtifactPut(Integer statusCode = 200, PasswordCredentials credentials = null) {
        server.expectPut(artifactPath, artifactFile, statusCode, credentials)
    }

    void expectArtifactSha1Put(PasswordCredentials credentials) {
        expectArtifactSha1Put(200, credentials)
    }

    void expectArtifactSha1Put(Integer statusCode = 200, PasswordCredentials credentials = null) {
        server.expectPut(artifactSha1Path, artifactSha1File, statusCode, credentials)
    }

    void expectArtifactMd5Put(PasswordCredentials credentials) {
        expectArtifactMd5Put(200, credentials)
    }

    void expectArtifactMd5Put(Integer statusCode = 200, PasswordCredentials credentials = null) {
        server.expectPut(artifactMd5Path, artifactMd5File, statusCode, credentials)
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
