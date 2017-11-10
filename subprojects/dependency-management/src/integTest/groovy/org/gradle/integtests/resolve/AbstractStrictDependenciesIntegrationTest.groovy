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
import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.gradle.integtests.fixtures.resolve.ResolveTestFixture
import org.gradle.test.fixtures.maven.DelegatingMavenModule
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.junit.runner.RunWith

@RunWith(GradleMetadataResolveRunner)
abstract class AbstractStrictDependenciesIntegrationTest extends AbstractHttpDependencyResolutionTest {
    final ResolveTestFixture resolve = new ResolveTestFixture(buildFile, "conf")

    def getRepository() {
        """
            repositories {
                maven { 
                   url "${mavenHttpRepo.uri}"
                   ${GradleMetadataResolveRunner.isGradleMetadataEnabled()?'useGradleMetadata()':''}
                }
            }
        """
    }

    MavenPublishedModule module(String group, String name, String version) {
        def module = new MavenPublishedModule(mavenHttpRepo.module(group, name, version))
        if (GradleMetadataResolveRunner.isGradleMetadataEnabled()) {
            module.withModuleMetadata()
        }
        module
    }

    def setup() {
        settingsFile << "rootProject.name = 'test'"
        resolve.prepare()
        server.start()
    }

    static class MavenPublishedModule extends DelegatingMavenModule<MavenHttpModule> {
        private final MavenHttpModule module

        MavenPublishedModule(MavenHttpModule module) {
            super(module)
            this.module = module
        }

        void assertGetRootMetadata() {
            if (GradleContextualExecuter.parallel) {
                module.rootMetaData.allowGetOrHead()
            } else {
                module.rootMetaData.expectGet()
            }
        }

        void assertGetMetadata() {
            if (GradleContextualExecuter.parallel) {
                module.allowAll()
            } else {
                module.pom.expectGet()
                if (GradleMetadataResolveRunner.isGradleMetadataEnabled()) {
                    module.moduleMetadata.expectGet()
                }
            }
        }

        void assertGetArtifact() {
            if (GradleContextualExecuter.parallel) {
                module.allowAll()
            } else {
                module.artifact.expectGet()
            }
        }

    }
}
