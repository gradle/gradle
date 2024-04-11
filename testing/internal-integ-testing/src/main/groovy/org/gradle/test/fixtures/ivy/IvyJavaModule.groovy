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

package org.gradle.test.fixtures.ivy

import org.gradle.api.JavaVersion
import org.gradle.api.attributes.java.TargetJvmVersion
import org.gradle.test.fixtures.ModuleArtifact
import org.gradle.test.fixtures.PublishedJavaModule
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.server.http.DelegatingIvyModule

class IvyJavaModule extends DelegatingIvyModule<IvyFileModule> implements PublishedJavaModule {
    final IvyFileModule backingModule
    final List<ModuleArtifact> additionalArtifacts = []

    IvyJavaModule(IvyFileModule backingModule) {
        super(backingModule)
        this.backingModule = backingModule
        this.backingModule.attributes[TargetJvmVersion.TARGET_JVM_VERSION_ATTRIBUTE.name] = JavaVersion.current().majorVersion
    }

    @Override
    ModuleArtifact getIvy() {
        backingModule.ivy
    }

    @Override
    ModuleArtifact getJar() {
        backingModule.jar
    }

    @Override
    ModuleArtifact getModuleMetadata() {
        backingModule.moduleMetadata
    }

    @Override
    PublishedJavaModule withClassifiedArtifact(String classifier, String extension) {
        def module = backingModule.moduleArtifact(classifier: classifier, ext: extension)
        additionalArtifacts << module
        this
    }

    @Override
    void assertNoDependencies() {
        assert backingModule.parsedIvy.dependencies.isEmpty()
        assertApiDependencies()
    }

    @Override
    void assertApiDependencies(String... expected) {
        if (expected.length == 0) {
            assert parsedModuleMetadata.variant('apiElements').dependencies.empty
            assert parsedIvy.dependencies.findAll { it.value.confs.contains('compile') }.isEmpty()
        } else {
            assert parsedModuleMetadata.variant('apiElements').dependencies*.coords as Set == expected as Set
            parsedIvy.assertConfigurationDependsOn('compile', expected)
        }
    }

    @Override
    void assertRuntimeDependencies(String... expected) {
        if (expected.length == 0) {
            assert parsedModuleMetadata.variant('runtimeElements').dependencies.empty
            assert parsedIvy.dependencies.findAll { it.value.conf == 'runtime' }.isEmpty()
        } else {
            assert parsedModuleMetadata.variant('runtimeElements').dependencies*.coords as Set == expected as Set
            parsedIvy.assertConfigurationDependsOn('runtime', expected)
        }

    }

    @Override
    void assertPublishedAsJavaModule() {
        assertPublished()
    }

    @Override
    void assertPublishedAsWebModule() {
        super.assertPublished()

        def war = backingModule.file(type:'war')
        List<String> expectedArtifacts = [war.name, backingModule.moduleMetadataFile.name, backingModule.ivyFile.name]
        expectedArtifacts.addAll(additionalArtifacts.file.name)
        backingModule.assertArtifactsPublished(expectedArtifacts as String[])

        // Verify Gradle metadata particulars
        // TODO: Should probably map to 'runtime', see org.gradle.api.internal.java.WebApplication
        assert backingModule.parsedModuleMetadata.variants*.name as Set == ['master'] as Set
        assert backingModule.parsedModuleMetadata.variant('master').files*.name == [war.name]
    }

    @Override
    void assertPublished() {
        super.assertPublished()

        List<String> expectedArtifacts = [backingModule.jarFile.name, backingModule.moduleMetadataFile.name, backingModule.ivyFile.name]
        expectedArtifacts.addAll(additionalArtifacts.file.name)
        backingModule.assertArtifactsPublished(expectedArtifacts as String[])

        // Verify Gradle metadata particulars
        assert backingModule.parsedModuleMetadata.variants*.name as Set == ['apiElements', 'runtimeElements'] as Set
        assert backingModule.parsedModuleMetadata.variant('apiElements').files*.name == [backingModule.jarFile.name]
        assert backingModule.parsedModuleMetadata.variant('runtimeElements').files*.name == [backingModule.jarFile.name]
    }

    void assertNotPublished() {
        backingModule.assertNotPublished()
    }

    TestFile getModuleDir() {
        backingModule.moduleDir
    }

    IvyJavaModule removeGradleMetadataRedirection() {
        backingModule.removeGradleMetadataRedirection()
        this
    }
}
