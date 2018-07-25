/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.resource.gcs.fixtures

import org.gradle.test.fixtures.maven.DelegatingMavenModule
import org.gradle.test.fixtures.maven.MavenFileModule
import org.gradle.test.fixtures.maven.RemoteMavenModule

class MavenGcsModule extends DelegatingMavenModule<MavenGcsModule> implements RemoteMavenModule {
    MavenFileModule backingModule
    GcsServer server
    String bucket
    String repositoryPath

    MavenGcsModule(GcsServer server, MavenFileModule backingModule, String repositoryPath, String bucket) {
        super(backingModule)
        this.bucket = bucket
        this.server = server
        this.backingModule = backingModule
        this.repositoryPath = repositoryPath
    }

    GcsArtifact getPom() {
        return new GcsArtifact(server, pomFile, repositoryPath, bucket)
    }

    GcsArtifact getModuleMetadata() {
        return new GcsArtifact(server, backingModule.moduleMetadata.file, repositoryPath, bucket)
    }

    GcsArtifact getArtifact() {
        return new GcsArtifact(server, artifactFile, repositoryPath, bucket)
    }

    @Override
    GcsArtifact getRootMetaData() {
        return new GcsArtifact(server, backingModule.rootMetaDataFile, repositoryPath, bucket)
    }

    GcsArtifact getMetaData() {
        new GcsArtifact(server, backingModule.metaDataFile, repositoryPath, bucket)
    }
}
