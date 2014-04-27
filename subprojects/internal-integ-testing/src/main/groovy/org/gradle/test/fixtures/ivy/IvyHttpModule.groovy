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

package org.gradle.test.fixtures.ivy

import org.gradle.test.fixtures.HttpArtifact
import org.gradle.test.fixtures.HttpModule
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.HttpServer

class IvyHttpModule implements IvyModule, HttpModule {
    public final IvyHttpRepository repository
    private final IvyFileModule backingModule
    private final HttpServer server
    private final String prefix

    IvyHttpModule(IvyHttpRepository repository, HttpServer server, String prefix, IvyFileModule backingModule) {
        this.repository = repository
        this.prefix = prefix
        this.server = server
        this.backingModule = backingModule
    }

    String getOrganisation() {
        return backingModule.organisation
    }

    String getModule() {
        return backingModule.module
    }

    String getRevision() {
        return backingModule.revision
    }

    IvyDescriptor getParsedIvy() {
        return backingModule.parsedIvy
    }

    IvyHttpModule publish() {
        backingModule.publish()
        return this
    }

    IvyHttpModule publishWithChangedContent() {
        backingModule.publishWithChangedContent()
        return this
    }

    IvyHttpModule withNoMetaData() {
        backingModule.withNoMetaData()
        return this
    }

    IvyHttpModule withStatus(String status) {
        backingModule.withStatus(status)
        return this
    }

    IvyHttpModule dependsOn(String organisation, String module, String revision) {
        backingModule.dependsOn(organisation, module, revision)
        return this
    }

    IvyHttpModule dependsOn(Map<String, ?> attributes) {
        backingModule.dependsOn(attributes)
        return this
    }

    IvyHttpModule artifact(Map<String, ?> options = [:]) {
        backingModule.artifact(options)
        return this
    }

    IvyHttpModule undeclaredArtifact(Map<String, ?> options = [:]) {
        backingModule.undeclaredArtifact(options)
        return this
    }

    IvyHttpModule extendsFrom(Map<String, ?> attributes) {
        backingModule.extendsFrom(attributes)
        return this
    }

    IvyHttpModule configuration(Map<String, ?> options = [:], String name) {
        backingModule.configuration(options, name)
        return this
    }

    IvyHttpModule withXml(Closure action) {
        backingModule.withXml(action)
        return this
    }

    IvyHttpModule withExtraAttributes(Map extraAttributes) {
        backingModule.withExtraAttributes(extraAttributes)
        return this
    }

    IvyHttpModule withExtraInfo(Map extraInfo) {
        backingModule.withExtraInfo(extraInfo)
        return this
    }

    TestFile getIvyFile() {
        return backingModule.ivyFile
    }

    TestFile getJarFile() {
        return backingModule.jarFile
    }

    void allowAll() {
        server.allowGetOrHead(prefix, backingModule.moduleDir)
    }

    IvyModuleHttpArtifact getArtifact(Map<String, ?> options) {
        def backingFile = backingModule.file(options)
        return new IvyModuleHttpArtifact(server, prefix, backingModule.getArtifactFilePath(options), backingFile)
    }

    HttpArtifact getIvy() {
        return new IvyModuleHttpArtifact(server, prefix, backingModule.ivyFilePath, ivyFile)
    }

    HttpArtifact getJar() {
        return new IvyModuleHttpArtifact(server, prefix, backingModule.jarFilePath, jarFile)
    }

    void assertIvyAndJarFilePublished() {
        backingModule.assertIvyAndJarFilePublished()
    }

    private class IvyModuleHttpArtifact extends HttpArtifact {
        final filePath
        final TestFile backingFile

        IvyModuleHttpArtifact(HttpServer server, String modulePath, String filePath, TestFile backingFile) {
            super(server, modulePath)
            this.filePath = filePath;
            this.backingFile = backingFile
        }

        @Override
        TestFile getFile() {
            return backingFile
        }

        @Override
        protected TestFile getSha1File() {
            return backingModule.getSha1File(backingFile)
        }

        @Override
        protected TestFile getMd5File() {
            return backingModule.getMd5File(backingFile)
        }

        @Override
        protected String getPath() {
            "${modulePath}/${filePath}"
        }
    }
}


