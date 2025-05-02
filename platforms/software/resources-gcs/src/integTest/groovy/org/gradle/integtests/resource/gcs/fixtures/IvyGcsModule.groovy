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

import org.gradle.test.fixtures.ModuleArtifact
import org.gradle.test.fixtures.ivy.IvyFileModule
import org.gradle.test.fixtures.ivy.RemoteIvyModule
import org.gradle.test.fixtures.server.http.DelegatingIvyModule

class IvyGcsModule extends DelegatingIvyModule<IvyGcsModule> implements RemoteIvyModule {
    IvyFileModule backingModule
    String bucket
    GcsServer server
    String repositoryPath

    IvyGcsModule(GcsServer server, IvyFileModule backingModule, String repositoryPath, String bucket) {
        super(backingModule)
        this.bucket = bucket
        this.server = server
        this.backingModule = backingModule
        this.repositoryPath = repositoryPath
    }

    @Override
    GcsArtifact getIvy() {
        return new GcsArtifact(server, ivyFile, repositoryPath, bucket)
    }

    @Override
    GcsArtifact getJar() {
        return new GcsArtifact(server, jarFile, repositoryPath, bucket)
    }

    @Override
    ModuleArtifact getModuleMetadata() {
        return new GcsArtifact(server, moduleMetadataFile, repositoryPath, bucket)
    }
}
