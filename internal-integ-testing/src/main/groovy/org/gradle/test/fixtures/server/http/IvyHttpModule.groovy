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

import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.Hashing
import org.gradle.test.fixtures.HttpModule
import org.gradle.test.fixtures.ModuleArtifact
import org.gradle.test.fixtures.ResettableExpectations
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.ivy.IvyFileModule
import org.gradle.test.fixtures.ivy.RemoteIvyModule

class IvyHttpModule extends DelegatingIvyModule<IvyHttpModule> implements RemoteIvyModule, HttpModule, ResettableExpectations {
    public final IvyHttpRepository repository
    private final IvyFileModule backingModule
    private final HttpServer server
    private final String repoPrefix
    private final String prefix

    IvyHttpModule(IvyHttpRepository repository, HttpServer server, String repoPrefix, String prefix, IvyFileModule backingModule) {
        super(backingModule)
        this.repository = repository
        this.repoPrefix = repoPrefix
        this.prefix = prefix
        this.server = server
        this.backingModule = backingModule
    }

    IvyHttpModule withExtraAttributes(Map extraAttributes) {
        backingModule.withExtraAttributes(extraAttributes)
        return this
    }

    IvyHttpModule withExtraInfo(Map extraInfo) {
        backingModule.withExtraInfo(extraInfo)
        return this
    }

    IvyHttpModule withBranch(String branch) {
        backingModule.withBranch(branch)
        return this
    }

    IvyModuleHttpArtifact getArtifact(Map<String, ?> options = [:]) {
        def artifact = backingModule.moduleArtifact(options)
        return new IvyModuleHttpArtifact(server, prefix, artifact)
    }

    IvyHttpModule allowAll() {
        server.allowGetOrHead(prefix, backingModule.moduleDir)
        return this
    }

    IvyModuleHttpArtifact getIvy() {
        return new IvyModuleHttpArtifact(server, prefix, backingModule.ivy)
    }

    IvyModuleHttpArtifact getJar() {
        return new IvyModuleHttpArtifact(server, prefix, backingModule.jar)
    }

    @Override
    IvyModuleHttpArtifact getModuleMetadata() {
        return new IvyModuleHttpArtifact(server, prefix, backingModule.moduleMetadata)
    }

    @Override
    void resetExpectations() {
        server?.resetExpectations()
    }

    private class IvyModuleHttpArtifact extends HttpArtifact {
        final ModuleArtifact backingArtifact

        IvyModuleHttpArtifact(HttpServer server, String modulePath, ModuleArtifact backingArtifact) {
            super(server, modulePath)
            this.backingArtifact = backingArtifact
        }

        @Override
        String getPath() {
            return repoPrefix + '/' + backingArtifact.path
        }

        @Override
        TestFile getFile() {
            return backingArtifact.file
        }

        @Override
        String getName() {
            return backingArtifact.name
        }

        @Override
        protected TestFile getSha1File() {
            return backingModule.getSha1File(file)
        }

        @Override
        protected TestFile getMd5File() {
            return backingModule.getMd5File(file)
        }

        @Override
        protected TestFile getSha256File() {
            backingModule.getSha256File(file)
        }

        @Override
        protected TestFile getSha512File() {
            backingModule.getSha512File(file)
        }

        @Override
        void verifyChecksums() {
            // MD5 not published for ivy modules
            def sha1File = getSha1File()
            sha1File.assertIsFile()
            assert HashCode.fromString(sha1File.text) == Hashing.sha1().hashFile(file)
        }
    }
}


