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
        backingModule.moduleDir.mkdirs()
    }

    protected String getModuleVersionPath() {
        "${moduleRootPath}/${backingModule.version}"
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
        "$moduleRootPath/$metaDataFile.name"
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

    void expectRootMetaDataGetMissing() {
        server.expectGetMissing(rootMetaDataPath)
    }

    void expectMetaDataPut(int statusCode = 200) {
        server.expectPut(metaDataPath, backingModule.metaDataFile, statusCode)
    }

    void expectMetaDataSha1Put(int statusCode = 200) {
        server.expectPut(sha1Path(metaDataPath), sha1File(metaDataFile), statusCode)
    }

    void expectMetaDataMd5Put(int statusCode = 200) {
        server.expectPut(md5Path(metaDataPath), md5File(metaDataFile), statusCode)
    }

    void expectRootMetaDataPut(int statusCode = 200) {
        server.expectPut(rootMetaDataPath, rootMetaDataFile, statusCode)
    }

    void expectRootMetaDataSha1Put(int statusCode = 200) {
        server.expectPut(sha1Path(rootMetaDataPath), sha1File(rootMetaDataFile), statusCode)
    }

    void expectRootMetaDataMd5Put(int statusCode = 200) {
        server.expectPut(md5Path(rootMetaDataPath), md5File(rootMetaDataFile), statusCode)
    }

    void expectPomPut(int statusCode = 200) {
        server.expectPut(pomPath, pomFile, statusCode)
    }

    void expectPomSha1Put(int statusCode = 200) {
        server.expectPut(sha1Path(pomPath), sha1File(pomFile), statusCode)
    }

    void expectPomMd5Put(int statusCode = 200) {
        server.expectPut(md5Path(pomPath), md5File(pomFile), statusCode)
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

    void expectArtifactGet() {
        server.expectGet(getArtifactPath(), artifactFile)
    }

    String getArtifactPath() {
        "$moduleVersionPath/$artifactFile.name"
    }

    String getPomPath() {
        "$moduleVersionPath/$pomFile.name"
    }

    void expectArtifactHead() {
        server.expectHead("$moduleVersionPath/$artifactFile.name", artifactFile)
    }

    void expectArtifactGetMissing() {
        server.expectGetMissing("$moduleVersionPath/$missingArtifactName")
    }

    void expectArtifactHeadMissing() {
        server.expectHeadMissing("$moduleVersionPath/$missingArtifactName")
    }

    void expectArtifactSha1Get() {
        server.expectGet(getArtifactSha1Path(), backingModule.sha1File(artifactFile))
    }

    TestFile getArtifactSha1File() {
        backingModule.artifactSha1File
    }

    String getArtifactSha1Path() {
        "${artifactPath}.sha1"
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

    private String getMissingArtifactName() {
        if (backingModule.version.endsWith("-SNAPSHOT")) {
            return "${backingModule.artifactId}-${backingModule.version}.jar"
        } else {
            return artifactFile.name
        }
    }

    void expectArtifactPut(int statusCode = 200) {
        server.expectPut(artifactPath, artifactFile, statusCode)
    }

    void expectArtifactSha1Put(int statusCode = 200) {
        server.expectPut(artifactSha1Path, artifactSha1File, statusCode)
    }

    void expectArtifactMd5Put(int statusCode = 200) {
        server.expectPut(artifactMd5Path, artifactMd5File, statusCode)
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
