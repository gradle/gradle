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
import org.gradle.integtests.fixtures.ExperimentalFeaturesFixture
import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.publish.RemoteRepositorySpec
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.junit.runner.RunWith

@RunWith(GradleMetadataResolveRunner)
abstract class AbstractModuleDependencyResolveTest extends AbstractHttpDependencyResolutionTest {
    final ResolveTestFixture resolve = new ResolveTestFixture(buildFile, "conf")

    private final RemoteRepositorySpec repoSpec = new RemoteRepositorySpec()

    boolean useIvy() {
        GradleMetadataResolveRunner.useIvy()
    }

    boolean isGradleMetadataEnabled() {
        GradleMetadataResolveRunner.isGradleMetadataEnabled()
    }

    String getRootProjectName() { 'test' }

    void resetExpectations(boolean expectFailure = false) {
        server.resetExpectations(expectFailure)
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
        if (GradleMetadataResolveRunner.useIvy()) {
            def httpModule = ivyHttpRepo.module(group, module, version)
            return httpModule.ivy.uri
        } else {
            def httpModule = mavenHttpRepo.module(group, module, version)
            return httpModule.pom.uri
        }
    }

    String triedMetadata(String group, String module, String version, boolean includeJar = false) {
        def uris = []
        if (GradleMetadataResolveRunner.useIvy()) {
            def httpModule = ivyHttpRepo.module(group, module, version)
            uris << httpModule.ivy.uri
            if (GradleMetadataResolveRunner.gradleMetadataEnabled) {
                uris << httpModule.moduleMetadata.uri
            }
            if (includeJar) {
                uris << httpModule.artifact.uri
            }
        } else {
            def httpModule = mavenHttpRepo.module(group, module, version)
            uris << httpModule.pom.uri
            if (GradleMetadataResolveRunner.gradleMetadataEnabled) {
                uris << httpModule.moduleMetadata.uri
            }
            if (includeJar) {
                uris << httpModule.artifact.uri
            }
        }
        uris.collect { "    $it" }.join('\n')
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

    def getRepository() {
        useIvy() ? ivyRepository : mavenRepository
    }

    def setup() {
        settingsFile << "rootProject.name = '$rootProjectName'"
        if (GradleMetadataResolveRunner.isGradleMetadataEnabled()) {
            ExperimentalFeaturesFixture.enable(settingsFile)
        }
        resolve.prepare()
        buildFile << """
            $repository

            configurations {
                conf
            }
        """
    }

    void repository(@DelegatesTo(RemoteRepositorySpec) Closure<Void> spec) {
        spec.delegate = repoSpec
        spec()
    }

    void repositoryInteractions(@DelegatesTo(RemoteRepositorySpec) Closure<Void> spec) {
        RemoteRepositorySpec.DEFINES_INTERACTIONS.set(true)
        try {
            spec.delegate = repoSpec
            spec()
            repoSpec.build(useIvy() ? ivyHttpRepo : mavenHttpRepo)
        } finally {
            RemoteRepositorySpec.DEFINES_INTERACTIONS.set(false)
        }
    }

}
