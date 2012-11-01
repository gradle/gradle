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

import org.gradle.util.TestFile

class IvyHttpRepository implements IvyRepository {
    private final HttpServer server
    private final IvyFileRepository backingRepository
    private final String contextPath

    IvyHttpRepository(HttpServer server, String contextPath = "/repo", IvyFileRepository backingRepository) {
        if (!contextPath.startsWith("/")) {
            throw new IllegalArgumentException("Context path must start with '/'")
        }
        this.contextPath = contextPath
        this.server = server
        this.backingRepository = backingRepository
    }

    URI getUri() {
        return new URI("http://localhost:${server.port}${contextPath}")
    }

    String getIvyPattern() {
        return "$uri/${backingRepository.baseIvyPattern}"
    }

    String getArtifactPattern() {
        return "$uri/${backingRepository.baseArtifactPattern}"
    }

    void expectDirectoryListGet(String organisation, String module) {
        server.expectGetDirectoryListing("$contextPath/$organisation/$module/", backingRepository.module(organisation, module, "1.0").moduleDir.parentFile)
    }

    IvyHttpModule module(String organisation, String module, Object revision = "1.0") {
        return new IvyHttpModule(server, "$contextPath/$organisation/$module/$revision", backingRepository.module(organisation, module, revision))
    }
}

class IvyHttpModule extends AbstractIvyModule {
    private final IvyFileModule backingModule
    private final HttpServer server
    private final String prefix

    IvyHttpModule(HttpServer server, String prefix, IvyFileModule backingModule) {
        this.prefix = prefix
        this.server = server
        this.backingModule = backingModule
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

    IvyHttpModule artifact(Map<String, ?> options) {
        backingModule.artifact(options)
        return this
    }

    String getIvyFileUri() {
        return "http://localhost:${server.port}$prefix/$ivyFile.name"
    }

    TestFile getIvyFile() {
        return backingModule.ivyFile
    }

    String getJarFileUri() {
        return "http://localhost:${server.port}$prefix/$jarFile.name"
    }

    TestFile getJarFile() {
        return backingModule.jarFile
    }

    void allowAll() {
        server.allowGetOrHead(prefix, backingModule.moduleDir)
    }

    void expectIvyGet() {
        server.expectGet("$prefix/$ivyFile.name", ivyFile)
    }

    void expectIvyGetMissing() {
        server.expectGetMissing("$prefix/$ivyFile.name")
    }

    void expectIvyHead() {
        server.expectHead("$prefix/$ivyFile.name", ivyFile)
    }

    void expectIvySha1Get() {
        server.expectGet("$prefix/${ivyFile.name}.sha1", backingModule.sha1File(ivyFile))
    }

    void expectIvySha1GetMissing() {
        server.expectGetMissing("$prefix/${ivyFile.name}.sha1")
    }

    void expectJarGet() {
        server.expectGet("$prefix/$jarFile.name", jarFile)
    }

    void expectJarHead() {
        server.expectHead("$prefix/$jarFile.name", jarFile)
    }

    void expectJarSha1Get() {
        server.expectGet("$prefix/${jarFile.name}.sha1", backingModule.sha1File(jarFile))
    }

    void expectJarSha1GetMissing() {
        server.expectGetMissing("$prefix/${jarFile.name}.sha1")
    }

    void expectArtifactGet(String name) {
        def artifactFile = backingModule.artifactFile(name)
        server.expectGet("$prefix/$artifactFile.name", artifactFile)
    }
}


