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
import org.gradle.integtests.fixtures.extensions.GradleMetadataResolveInterceptor
import org.gradle.integtests.fixtures.extensions.GradleMetadataResolveTest
import org.gradle.integtests.fixtures.publish.RemoteRepositorySpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.HttpRepository

@GradleMetadataResolveTest
abstract class AbstractModuleDependencyResolveTest extends AbstractHttpDependencyResolutionTest {
    ResolveTestFixture resolve

    protected final RemoteRepositorySpec repoSpec = new RemoteRepositorySpec()

    boolean useIvy() {
        GradleMetadataResolveInterceptor.useIvy()
    }

    boolean useMaven() {
        !useIvy()
    }

    boolean isGradleMetadataPublished() {
        GradleMetadataResolveInterceptor.isGradleMetadataPublished()
    }

    boolean usesJavaLibraryVariants() {
        GradleMetadataResolveInterceptor.isGradleMetadataPublished() || useMaven()
    }

    String getTestConfiguration() { 'conf' }

    String getRootProjectName() { 'test' }

    void resetExpectations() {
        server.resetExpectations()
        repoSpec.nextStep()
    }

    String versionListingURI(String group, String module) {
        GradleMetadataResolveInterceptor.useIvy() ? "${ivyHttpRepo.uri}/$group/$module/" : "${mavenHttpRepo.uri}/${group.replace('.', '/')}/${module}/maven-metadata.xml"
    }

    String artifactURI(String group, String module, String version) {
        if (GradleMetadataResolveInterceptor.useIvy()) {
            def httpModule = ivyHttpRepo.module(group, module, version)
            return httpModule.artifact.uri
        } else {
            def httpModule = mavenHttpRepo.module(group, module, version)
            return httpModule.artifact.uri
        }
    }

    String gradleMetadataURI(String group, String module, String version) {
        getMetadataUri(group, module, version, true)
    }

    String legacyMetadataURI(String group, String module, String version) {
        getMetadataUri(group, module, version, false)
    }

    private String getMetadataUri(String group, String module, String version, boolean gradleMetadata) {
        if (GradleMetadataResolveInterceptor.useIvy()) {
            def ivyModule = ivyHttpRepo.module(group, module, version)
            if (gradleMetadata) {
                return ivyModule.moduleMetadata.uri
            }
            return ivyModule.ivy.uri
        } else {
            def mavenModule = mavenHttpRepo.module(group, module, version)
            if (gradleMetadata) {
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

    boolean isDeclareRepositoriesInSettings() {
        false
    }

    def setup() {
        resolve = new ResolveTestFixture(buildFile, testConfiguration)
        resolve.expectDefaultConfiguration(usesJavaLibraryVariants() ? "runtime" : "default")
        settingsFile << "rootProject.name = '$rootProjectName'"
        def repoBlock = repositoryDeclaration
        if (declareRepositoriesInSettings) {
            settingsFile << """
                dependencyResolutionManagement {
                    $repoBlock
                }
            """
            repoBlock = ''
        }
        resolve.prepare()
        buildFile << """
            $repoBlock

            configurations {
                $testConfiguration
            }
        """
        resolve.addJavaEcosystem()
    }

    void repository(@DelegatesTo(RemoteRepositorySpec) Closure<Void> spec) {
        spec.delegate = repoSpec
        spec()
    }

    void repositoryInteractions(HttpRepository.MetadataType metadataType = getRepositoryMetadataType(),
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
        return useIvy() ? getIvyHttpRepo(metadataType) : getMavenHttpRepo(metadataType)
    }

    HttpRepository getRepository() {
        getHttpRepository(getRepositoryMetadataType())
    }

    HttpRepository.MetadataType getRepositoryMetadataType() {
        gradleMetadataPublished ? HttpRepository.MetadataType.DEFAULT : HttpRepository.MetadataType.ONLY_ORIGINAL
    }

}
