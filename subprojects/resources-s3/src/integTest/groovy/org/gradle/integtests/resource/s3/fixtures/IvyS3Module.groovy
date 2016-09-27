/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.integtests.resource.s3.fixtures

import org.gradle.test.fixtures.Module
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.ivy.IvyDescriptor
import org.gradle.test.fixtures.ivy.IvyFileModule
import org.gradle.test.fixtures.ivy.IvyModule
import org.gradle.test.fixtures.ivy.RemoteIvyModule

class IvyS3Module implements RemoteIvyModule {
    IvyFileModule backingModule
    String bucket
    S3Server server
    String repositoryPath

    IvyS3Module(S3Server server, IvyFileModule backingModule, String repositoryPath, String bucket) {
        this.bucket = bucket
        this.server = server
        this.backingModule = backingModule
        this.repositoryPath = repositoryPath
    }

    @Override
    void assertPublished() {
        backingModule.assertPublished()
    }

    @Override
    void assertArtifactsPublished(String... names) {
        backingModule.assertArtifactsPublished(names)
    }

    @Override
    void assertPublishedAsJavaModule() {
        backingModule.assertPublishedAsJavaModule()
    }

    @Override
    String getOrganisation() {
        return backingModule.getOrganisation()
    }

    @Override
    S3Artifact getIvy() {
        return new S3Artifact(server, ivyFile, repositoryPath, bucket)
    }

    @Override
    S3Artifact getJar() {
        return new S3Artifact(server, jarFile, repositoryPath, bucket)
    }

    @Override
    String getGroup() {
        return backingModule.group
    }

    @Override
    String getModule() {
        return backingModule.module
    }

    @Override
    String getVersion() {
        return backingModule.version
    }

    @Override
    String getRevision() {
        return backingModule.revision
    }

    @Override
    TestFile getIvyFile() {
        return backingModule.ivyFile
    }

    @Override
    TestFile getJarFile() {
        return backingModule.jarFile
    }

    @Override
    IvyModule withNoMetaData() {
        return backingModule.withNoMetaData()
    }

    @Override
    IvyModule withStatus(String status) {
        return backingModule.withStatus(status)
    }

    @Override
    IvyModule dependsOn(String organisation, String module, String revision) {
        return backingModule.dependsOn(organisation, module, revision)
    }

    @Override
    IvyModule extendsFrom(Map<String, ?> attributes) {
        return backingModule.extendsFrom(attributes)
    }

    @Override
    IvyModule dependsOn(Map<String, ?> attributes) {
        return backingModule.dependsOn(attributes)
    }

    @Override
    IvyModule dependsOn(Map<String, ?> attributes, Module module) {
        return backingModule.dependsOn(attributes, module)
    }

    @Override
    IvyModule dependsOn(Module module) {
        return backingModule.dependsOn(module)
    }

    @Override
    IvyModule artifact(Map<String, ?> options) {
        return backingModule.artifact(options)
    }

    @Override
    IvyModule undeclaredArtifact(Map<String, ?> options) {
        return backingModule.undeclaredArtifact(options)
    }

    @Override
    IvyModule withXml(Closure action) {
        return backingModule.withXml(action)
    }

    @Override
    IvyModule configuration(String name) {
        return backingModule.configuration(name)
    }

    @Override
    IvyModule configuration(Map<String, ?> options, String name) {
        return backingModule.configuration(options, name)
    }

    @Override
    IvyModule publishWithChangedContent() {
        return backingModule.publishWithChangedContent()
    }

    @Override
    IvyModule publish() {
        return backingModule.publish()
    }

    @Override
    IvyDescriptor getParsedIvy() {
        return backingModule.getParsedIvy()
    }

    @Override
    void assertIvyAndJarFilePublished() {
        backingModule.assertIvyAndJarFilePublished()
    }
}
