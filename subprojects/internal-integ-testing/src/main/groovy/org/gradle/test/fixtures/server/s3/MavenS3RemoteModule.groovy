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
package org.gradle.test.fixtures.server.s3

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenRemoteModule
import org.gradle.test.fixtures.maven.MavenRemoteResource
import org.gradle.test.fixtures.maven.ModuleDescriptor

class MavenS3RemoteModule implements MavenRemoteModule {
    S3StubServer server
    String bucket
    String repositoryPath
    private File baseDir
    private ModuleDescriptor moduleDescriptor

    MavenS3RemoteModule(S3StubServer server, File baseDir, String bucket, String repositoryPath, ModuleDescriptor moduleDescriptor) {
        this.moduleDescriptor = moduleDescriptor
        this.baseDir = baseDir
        this.bucket = bucket
        this.server = server
        this.repositoryPath = repositoryPath
    }

    @Override
    MavenRemoteResource getPomFile() {
        resource(publicationDir(moduleDescriptor.artifactName('.pom')))
    }

    @Override
    MavenRemoteResource getArtifactFile(String type) {
        resource(publicationDir("${moduleDescriptor.artifactName(type)}"))
    }

    @Override
    MavenRemoteResource getJar() {
        resource(publicationDir("${moduleDescriptor.artifactName('.jar')}"))
    }

    @Override
    MavenRemoteResource getJavaSource() {
        resource(publicationDir("${moduleDescriptor.artifactName('-sources.jar')}"))
    }

    @Override
    MavenRemoteResource getJavadoc() {
        resource(publicationDir("${moduleDescriptor.artifactName('-javadoc.jar')}"))
    }

    @Override
    TestFile getStorageDirectory() {
        new TestFile(server.storageDirectory, "$bucket/$repositoryPath/${moduleDescriptor.artifactDirectory()}")
    }

    @Override
    MavenRemoteResource getMetaData() {
        resource(publicationDir("maven-metadata.xml"))
    }

    @Override
    MavenRemoteResource getRootMetaData() {
        resource(publicationRoot("maven-metadata.xml"))
    }

    MavenRemoteResource resource(String filePath) {
        if (moduleDescriptor.isSnapshot()) {
            return new MavenRemoteS3SnapshotResource(server, baseDir, bucket, repositoryPath, filePath)
        }
        new MavenRemoteS3Resource(server, baseDir, bucket, repositoryPath, filePath)
    }

    String publicationRoot(String file) {
        "${moduleDescriptor.rootDirectory()}/$file"
    }

    String publicationDir(String file) {
        "${moduleDescriptor.artifactDirectory()}/$file"
    }
}
