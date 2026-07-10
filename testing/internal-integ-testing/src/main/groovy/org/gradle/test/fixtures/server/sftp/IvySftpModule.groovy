/*
 * Copyright 2013 the original author or authors.
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

import org.gradle.test.fixtures.ivy.IvyFileModule
import org.gradle.test.fixtures.ivy.RemoteIvyModule
import org.gradle.test.fixtures.server.http.DelegatingIvyModule

class IvySftpModule extends DelegatingIvyModule<IvySftpModule> implements RemoteIvyModule {

    public final IvySftpRepository repository
    private SFTPServer server
    private IvyFileModule backingModule

    IvySftpModule(IvySftpRepository repository, SFTPServer server, IvyFileModule backingModule) {
        super(backingModule)
        this.repository = repository
        this.server = server
        this.backingModule = backingModule
    }

    SftpArtifact getIvy() {
        return new SftpArtifact(server, ivyFile)
    }

    SftpArtifact getJar() {
        return new SftpArtifact(server, jarFile)
    }

    @Override
    SftpArtifact getModuleMetadata() {
        return new SftpArtifact(server, moduleMetadataFile)
    }
}
