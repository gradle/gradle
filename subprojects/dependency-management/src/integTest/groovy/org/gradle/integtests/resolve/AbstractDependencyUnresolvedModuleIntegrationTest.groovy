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
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.test.fixtures.maven.MavenRepository
import org.gradle.test.fixtures.server.http.MavenHttpModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository

import static org.gradle.internal.resource.transport.http.JavaSystemPropertiesHttpTimeoutSettings.SOCKET_TIMEOUT_SYSTEM_PROPERTY

abstract class AbstractDependencyUnresolvedModuleIntegrationTest extends AbstractHttpDependencyResolutionTest {
    private static final String GROUP_ID = 'group'
    private static final String VERSION = '1.0'
    TestFile downloadedLibsDir
    MavenHttpModule moduleA

    def setup() {
        moduleA = publishMavenModule(mavenHttpRepo, 'a')
        downloadedLibsDir = file('build/libs')
        executer.beforeExecute {
            executer.withArgument("-D${SOCKET_TIMEOUT_SYSTEM_PROPERTY}=1000")
        }
    }

    protected String mavenRepository(MavenRepository repo) {
        """
            repositories {
                maven { url "${repo.uri}"}
            }
        """
    }

    protected String customConfigDependencyAssignment(String... modules) {
        """
            configurations {
                deps
            }

            dependencies {
                deps ${modules.collect { "'${it}'" }.join(', ')}
            }
        """
    }

    protected String customConfigDependencyAssignment(MavenHttpModule... modules) {
        customConfigDependencyAssignment(modules.collect { "${mavenModuleCoordinates(it)}" } as String[])
    }

    protected String configSyncTask() {
        """
            task resolve(type: Sync) {
                from configurations.deps
                into "\$buildDir/libs"
            }
        """
    }

    protected void assertDependencySkipped(MavenModule module) {
        failure.assertHasCause("Could not resolve ${mavenModuleCoordinates(module)}.")
        failure.assertHasCause("Skipped due to earlier error")
    }

    protected String mavenModuleCoordinates(MavenHttpModule module) {
        "$module.groupId:$module.artifactId:$module.version"
    }

    protected String mavenModuleRepositoryPath(MavenHttpModule module) {
        "$module.groupId/$module.artifactId/$module.version/$module.artifactId-$module.version"
    }

    protected MavenHttpModule publishMavenModule(MavenHttpRepository mavenHttpRepo, String artifactId) {
        mavenHttpRepo.module(GROUP_ID, artifactId, VERSION).publish()
    }

    protected void assertDependencyMetaDataReadTimeout(MavenModule module) {
        failure.assertHasCause("Could not resolve ${mavenModuleCoordinates(module)}.")
        failure.assertHasCause("Could not get resource '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.pom'.")
        failure.assertHasCause("Could not GET '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.pom'.")
        failure.assertHasCause("Read timed out")
    }
}
