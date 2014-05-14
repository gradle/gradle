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

import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.ivy.IvyDescriptor
import org.gradle.test.fixtures.ivy.IvyFileModule
import org.gradle.test.fixtures.ivy.IvyModule
import org.gradle.test.fixtures.ivy.RemoteIvyModule

class IvySftpModule implements RemoteIvyModule {

    public final IvySftpRepository repository
    private SFTPServer server
    private IvyFileModule backingModule
    
    IvySftpModule(IvySftpRepository repository, SFTPServer server, IvyFileModule backingModule) {
        this.repository = repository
        this.server = server
        this.backingModule = backingModule
    }

    TestFile getIvyFile() {
        return backingModule.ivyFile
    }

    TestFile getJarFile() {
        return backingModule.jarFile
    }

    IvyModule withNoMetaData() {
        return backingModule.withNoMetaData()
    }

    IvyModule withStatus(String status) {
        return backingModule.withStatus(status)
    }

    IvyModule dependsOn(String organisation, String module, String revision) {
        return backingModule.dependsOn(organisation, module, revision)
    }

    IvyModule extendsFrom(Map<String, ?> attributes) {
        return backingModule.extendsFrom(attributes)
    }

    IvyModule dependsOn(Map<String, ?> attributes) {
        return backingModule.dependsOn(attributes)
    }

    IvyModule artifact(Map<String, ?> options) {
        return backingModule.artifact(options)
    }

    IvyModule undeclaredArtifact(Map<String, ?> options) {
        return backingModule.undeclaredArtifact(options)
    }

    IvyModule withXml(Closure action) {
        return backingModule.withXml(action)
    }

    IvyModule configuration(String name) {
        return backingModule.configuration(name)
    }

    IvyModule configuration(Map<String, ?> options, String name) {
        return backingModule.configuration(options, name)
    }

    IvyModule publishWithChangedContent() {
        return backingModule.publishWithChangedContent()
    }

    IvyModule publish() {
        return backingModule.publish()
    }

    IvyDescriptor getParsedIvy() {
        return backingModule.parsedIvy
    }

    void assertIvyAndJarFilePublished() {
        backingModule.assertIvyAndJarFilePublished()
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

    SftpResource getIvy() {
        return new SftpResource(server, ivyFile)
    }

    SftpResource getJar() {
        return new SftpResource(server, jarFile)
    }
}
