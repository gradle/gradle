/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.test.fixtures.server.sftp

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenFileModule
import org.gradle.test.fixtures.maven.MavenMetaData
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.test.fixtures.maven.MavenPom

class MavenSftpModule implements MavenModule {
    MavenFileModule backingModule
    SFTPServer server

    MavenSftpModule(SFTPServer server, MavenFileModule backingModule) {
        this.server = server
        this.backingModule = backingModule
    }

    MavenModule publish() {
        backingModule.publish()
    }

    MavenModule publishPom() {
        backingModule.publishPom()
    }

    MavenModule publishWithChangedContent() {
        backingModule.publishWithChangedContent()
    }

    MavenModule withNonUniqueSnapshots() {
        backingModule.withNonUniqueSnapshots()
    }

    MavenModule parent(String group, String artifactId, String version) {
        backingModule.parent(group, artifactId, version)
    }

    MavenModule dependsOn(String group, String artifactId, String version) {
        backingModule.dependsOn(group, artifactId, version)
    }

    MavenModule dependsOn(String group, String artifactId, String version, String type) {
        backingModule.dependsOn(group, artifactId, version, type)
    }

    MavenModule hasPackaging(String packaging) {
        backingModule.hasPackaging(packaging)
    }

    MavenModule hasType(String type) {
        backingModule.hasPackaging(type)
    }

    TestFile getPomFile() {
        backingModule.getPomFile()
    }

    TestFile getArtifactFile() {
        backingModule.getArtifactFile()
    }

    TestFile getMetaDataFile() {
        backingModule.getMetaDataFile()
    }

    MavenPom getParsedPom() {
        backingModule.getParsedPom()
    }

    MavenMetaData getRootMetaData() {
        backingModule.getRootMetaData()
    }

    SftpResource getPom() {
        new SftpResource(server, pomFile)
    }

    SftpResource getArtifact() {
        new SftpResource(server, artifactFile)
    }
}
