/*
 * Copyright 2021 the original author or authors.
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

import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.test.fixtures.maven.MavenFileRepository
import org.gradle.test.fixtures.maven.MavenModule
import org.gradle.test.fixtures.server.http.MavenHttpRepository
import spock.lang.Unroll

class DependencyResolutionFailureIntegrationTest extends AbstractDependencyUnresolvedModuleIntegrationTest {
    @Unroll
    @ToBeFixedForConfigurationCache
    def "fails build and #abortDescriptor repository search if HTTP connection #reason when resolving metadata"() {
        given:
        MavenHttpRepository backupMavenHttpRepo = new MavenHttpRepository(server, '/repo-2', new MavenFileRepository(file('maven-repo-2')))
        def backupA = publishMavenModule(backupMavenHttpRepo, 'a')

        buildFile << """
            ${mavenRepository(mavenHttpRepo)}
            ${mavenRepository(backupMavenHttpRepo)}
            ${customConfigDependencyAssignment(moduleA)}
            ${configSyncTask()}
        """

        when:
        moduleA.pom."$action"()
        if (!abort) {
            backupA.pom."${action}"()
        }
        fails('resolve')

        then:
        "$outcome"(moduleA)
        !downloadedLibsDir.isDirectory()

        where:
        reason                          | abort | action                  | outcome
        'exceeds timeout'               | true  | 'expectGetBlocking'     | 'assertDependencyMetaDataReadTimeout'
        'returns internal server error' | true  | 'expectGetBroken'       | 'assertDependencyMetaDataInternalServerError'
        'returns uncritical error'      | false | 'expectGetUnauthorized' | 'assertDependencyMetaDataUnauthorizedError'

        abortDescriptor = abort ? 'aborts' : 'does not abort'
    }

    @Unroll
    @ToBeFixedForConfigurationCache
    def "fails build and aborts repository search if HTTP connection #reason when resolving artifact for found module"() {
        given:
        MavenHttpRepository backupMavenHttpRepo = new MavenHttpRepository(server, '/repo-2', new MavenFileRepository(file('maven-repo-2')))
        publishMavenModule(backupMavenHttpRepo, 'a')

        buildFile << """
            ${mavenRepository(mavenHttpRepo)}
            ${mavenRepository(backupMavenHttpRepo)}
            ${customConfigDependencyAssignment(moduleA)}
            ${configSyncTask()}
        """

        when:
        moduleA.pom.expectGet()
        moduleA.artifact."$action"()
        fails('resolve')

        then:
        "$outcome"(moduleA)
        !downloadedLibsDir.isDirectory()

        where:
        reason                          | action                  | outcome
        'exceeds timeout'               | 'expectGetBlocking'     | 'assertDependencyArtifactReadTimeout'
        'returns internal server error' | 'expectGetBroken'       | 'assertDependencyArtifactInternalServerError'
        'returns uncritical error'      | 'expectGetUnauthorized' | 'assertDependencyArtifactUnauthorizedError'
    }

    @Unroll
    @ToBeFixedForConfigurationCache
    def "fails build and #abortDescriptor repository search if HTTP connection #reason when resolving dynamic version"() {
        given:
        MavenHttpRepository backupMavenHttpRepo = new MavenHttpRepository(server, '/repo-2', new MavenFileRepository(file('maven-repo-2')))
        publishMavenModule(backupMavenHttpRepo, 'a')

        buildFile << """
            ${mavenRepository(mavenHttpRepo)}
            ${mavenRepository(backupMavenHttpRepo)}
            ${customConfigDependencyAssignment('group:a:1.+')}
            ${configSyncTask()}
        """

        when:
        mavenHttpRepo.getModuleMetaData('group', 'a')."$action"()
        if (!abort) {
            backupMavenHttpRepo.getModuleMetaData('group', 'a')."$action"()
        }
        fails('resolve')

        then:
        "$outcome"('group', 'a', '1.+')
        !downloadedLibsDir.isDirectory()

        where:
        reason                          | abort | action                  | outcome
        'exceeds timeout'               | true  | 'expectGetBlocking'     | 'assertDependencyListingReadTimeout'
        'returns internal server error' | true  | 'expectGetBroken'       | 'assertDependencyListingInternalServerError'
        'returns uncritical error'      | false | 'expectGetUnauthorized' | 'assertDependencyListingUnauthorizedError'

        abortDescriptor = abort ? 'aborts' : 'does not abort'
    }

    private void assertDependencyListingReadTimeout(String group, String module, String version) {
        failure.assertHasCause("Could not resolve ${group}:${module}:${version}.")
        failure.assertHasCause("Failed to list versions for ${group}:${module}.")
        failure.assertHasCause("Could not get resource '${mavenHttpRepo.uri.toString()}/${group}/${module}/maven-metadata.xml'.")
        failure.assertHasCause("Unable to load Maven meta-data from ${mavenHttpRepo.uri.toString()}/${group}/${module}/maven-metadata.xml.")
        failure.assertHasCause("Could not GET '${mavenHttpRepo.uri.toString()}/${group}/${module}/maven-metadata.xml'.")
        failure.assertHasCause("Read timed out")
    }

    private void assertDependencyListingInternalServerError(String group, String module, String version) {
        failure.assertHasCause("Could not resolve ${group}:${module}:${version}.")
        failure.assertHasCause("Failed to list versions for ${group}:${module}.")
        failure.assertHasCause("Could not get resource '${mavenHttpRepo.uri.toString()}/${group}/${module}/maven-metadata.xml'.")
        failure.assertHasCause("Unable to load Maven meta-data from ${mavenHttpRepo.uri.toString()}/${group}/${module}/maven-metadata.xml.")
        failure.assertHasCause("Could not GET '${mavenHttpRepo.uri.toString()}/${group}/${module}/maven-metadata.xml'. Received status code 500 from server: broken")
    }

    private void assertDependencyListingUnauthorizedError(String group, String module, String version) {
        failure.assertHasCause("Could not resolve ${group}:${module}:${version}.")
        failure.assertHasCause("Failed to list versions for ${group}:${module}.")
        failure.assertHasCause("Could not get resource '${mavenHttpRepo.uri.toString()}/${group}/${module}/maven-metadata.xml'.")
        failure.assertHasCause("Unable to load Maven meta-data from ${mavenHttpRepo.uri.toString()}/${group}/${module}/maven-metadata.xml.")
        failure.assertHasCause("Could not GET '${mavenHttpRepo.uri.toString()}/${group}/${module}/maven-metadata.xml'. Received status code 401 from server: Unauthorized")
    }

    private void assertDependencyMetaDataInternalServerError(MavenModule module) {
        failure.assertHasCause("Could not resolve ${mavenModuleCoordinates(module)}.")
        failure.assertHasCause("Could not get resource '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.pom'.")
        failure.assertHasCause("Could not GET '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.pom'. Received status code 500 from server: broken")
    }

    private void assertDependencyMetaDataUnauthorizedError(MavenModule module) {
        failure.assertHasCause("Could not resolve ${mavenModuleCoordinates(module)}.")
        failure.assertHasCause("Could not get resource '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.pom'.")
        failure.assertHasCause("Could not GET '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.pom'. Received status code 401 from server: Unauthorized")
    }

    private void assertDependencyArtifactReadTimeout(MavenModule module) {
        failure.assertHasCause("Could not download ${module.artifactFile.name} (${mavenModuleCoordinates(module)})")
        failure.assertHasCause("Could not get resource '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.jar'.")
        failure.assertHasCause("Could not GET '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.jar'.")
        failure.assertHasCause("Read timed out")
    }

    private void assertDependencyArtifactInternalServerError(MavenModule module) {
        failure.assertHasCause("Could not download ${module.artifactFile.name} (${mavenModuleCoordinates(module)})")
        failure.assertHasCause("Could not get resource '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.jar'.")
        failure.assertHasCause("Could not GET '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.jar'. Received status code 500 from server: broken")
    }

    private void assertDependencyArtifactUnauthorizedError(MavenModule module) {
        failure.assertHasCause("Could not download ${module.artifactFile.name} (${mavenModuleCoordinates(module)})")
        failure.assertHasCause("Could not get resource '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.jar'.")
        failure.assertHasCause("Could not GET '${mavenHttpRepo.uri.toString()}/${mavenModuleRepositoryPath(module)}.jar'. Received status code 401 from server: Unauthorized")
    }
}
