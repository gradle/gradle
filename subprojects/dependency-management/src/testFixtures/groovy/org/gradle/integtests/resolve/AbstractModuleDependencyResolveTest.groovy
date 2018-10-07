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

package org.gradle.integtests.resolve

import org.gradle.integtests.fixtures.AbstractHttpDependencyResolutionTest
import org.gradle.integtests.fixtures.FeaturePreviewsFixture
import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.publish.RemoteRepositorySpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.HttpRepository
import org.junit.runner.RunWith

@RunWith(GradleMetadataResolveRunner)
abstract class AbstractModuleDependencyResolveTest extends AbstractHttpDependencyResolutionTest {
    ResolveTestFixture resolve

    protected final RemoteRepositorySpec repoSpec = new RemoteRepositorySpec()

    boolean useIvy() {
        GradleMetadataResolveRunner.useIvy()
    }

    boolean useMaven() {
        !useIvy()
    }

    boolean isGradleMetadataEnabled() {
        GradleMetadataResolveRunner.isGradleMetadataEnabled()
    }

    boolean isExperimentalEnabled() {
        GradleMetadataResolveRunner.isExperimentalResolveBehaviorEnabled()
    }

    boolean usesJavaLibraryVariants() {
        GradleMetadataResolveRunner.isGradleMetadataEnabled() || useMaven()
    }

    String getTestConfiguration() { 'conf' }

    String getRootProjectName() { 'test' }

    void resetExpectations() {
        server.resetExpectations()
        repoSpec.nextStep()
    }

    String versionListingURI(String group, String module) {
        GradleMetadataResolveRunner.useIvy() ? "${ivyHttpRepo.uri}/$group/$module/" : "${mavenHttpRepo.uri}/${group.replace('.', '/')}/${module}/maven-metadata.xml"
    }

    String artifactURI(String group, String module, String version) {
        if (GradleMetadataResolveRunner.useIvy()) {
            def httpModule = ivyHttpRepo.module(group, module, version)
            return httpModule.artifact.uri
        } else {
            def httpModule = mavenHttpRepo.module(group, module, version)
            return httpModule.artifact.uri
        }
    }

    String metadataURI(String group, String module, String version) {
        getMetadataUri(group, module, version, GradleMetadataResolveRunner.experimentalResolveBehaviorEnabled)
    }

    String legacyMetadataURI(String group, String module, String version) {
        getMetadataUri(group, module, version, false)
    }

    private String getMetadataUri(String group, String module, String version, boolean experimentalResolve) {
        if (GradleMetadataResolveRunner.useIvy()) {
            def ivyModule = ivyHttpRepo.module(group, module, version)
            if (experimentalResolve) {
                return ivyModule.moduleMetadata.uri
            }
            return ivyModule.ivy.uri
        } else {
            def mavenModule = mavenHttpRepo.module(group, module, version)
            if (experimentalResolve) {
                return mavenModule.moduleMetadata.uri
            }
            return mavenModule.pom.uri
        }
    }

    private String getMavenRepository() {
        """
            repositories {
                maven { 
                   url "${mavenHttpRepo.uri}"
                }
            }
        """
    }

    private String getIvyRepository() {
        """
            repositories {
                ivy { 
                   url "${ivyHttpRepo.uri}"
                }
            }
        """
    }

    void setMetadataSupplierClass(String clazz) {
        buildFile << supplierDeclaration(clazz)
    }

    String supplierDeclaration(String clazz) {
        """
            repositories."${useIvy()?'ivy':'maven'}".metadataSupplier = $clazz
        """
    }

    void setMetadataSupplierClassWithParams(String clazz, String... params) {
        buildFile << """
            repositories."${useIvy()?'ivy':'maven'}".setMetadataSupplier($clazz) { ${params.collect { "params($it)" }.join(';')} }
        """
    }

    void setMetadataListerClass(String clazz) {
        buildFile << """
            repositories."${useIvy()?'ivy':'maven'}".componentVersionsLister = $clazz
        """
    }

    void setMetadataListerClassWithParams(String clazz, String... params) {
        buildFile << """
            repositories."${useIvy()?'ivy':'maven'}".setComponentVersionsLister($clazz) { ${params.collect { "params($it)" }.join(';')} }
        """
    }

    def getRepositoryDeclaration() {
        useIvy() ? ivyRepository : mavenRepository
    }

    def setup() {
        resolve = new ResolveTestFixture(buildFile, testConfiguration)
        resolve.expectDefaultConfiguration(usesJavaLibraryVariants() ? "runtime" : "default")
        settingsFile << "rootProject.name = '$rootProjectName'"
        if (GradleMetadataResolveRunner.experimentalResolveBehaviorEnabled) {
            FeaturePreviewsFixture.enableGradleMetadata(settingsFile)
        }
        resolve.prepare()
        buildFile << """
            $repositoryDeclaration

            configurations {
                $testConfiguration
            }
        """
        resolve.addDefaultVariantDerivationStrategy()
    }

    void repository(@DelegatesTo(RemoteRepositorySpec) Closure<Void> spec) {
        spec.delegate = repoSpec
        spec()
    }

    void repositoryInteractions(HttpRepository.MetadataType metadataType = HttpRepository.MetadataType.DEFAULT,
                                @DelegatesTo(RemoteRepositorySpec) Closure<Void> spec) {
        RemoteRepositorySpec.DEFINES_INTERACTIONS.set(true)
        try {
            spec.delegate = repoSpec
            spec()
            repoSpec.build(getHttpRepository(metadataType))
        } finally {
            RemoteRepositorySpec.DEFINES_INTERACTIONS.set(false)
        }
    }

    private HttpRepository getHttpRepository(HttpRepository.MetadataType metadataType) {
        if (metadataType == HttpRepository.MetadataType.DEFAULT) {
            return useIvy() ? ivyHttpRepo : mavenHttpRepo
        }
        useIvy() ? ivyHttpRepo("repo", metadataType) : mavenHttpRepo("repo", metadataType)
    }

    HttpRepository getRepository() {
        getHttpRepository(HttpRepository.MetadataType.DEFAULT)
    }

}
