/*
 * Copyright 2007-2008 the original author or authors.
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

package org.gradle.api.publication.maven.internal.deployer

import org.gradle.api.artifacts.maven.PomFilterContainer
import org.gradle.api.internal.artifacts.mvnsettings.LocalMavenRepositoryLocator
import org.gradle.api.internal.artifacts.mvnsettings.MavenSettingsProvider
import org.gradle.api.publication.maven.internal.ArtifactPomContainer
import org.gradle.internal.logging.LoggingManagerInternal
import spock.lang.Specification

class DefaultGroovyMavenDeployerTest extends Specification {
    protected ArtifactPomContainer artifactPomContainerMock = Mock()
    protected PomFilterContainer pomFilterContainerMock = Mock()
    protected LoggingManagerInternal loggingManagerMock = Mock()
    protected MavenSettingsProvider mavenSettingsProvider = Mock()
    protected LocalMavenRepositoryLocator mavenRepositoryLocator = Mock()
    private DefaultGroovyMavenDeployer groovyMavenDeployer = new DefaultGroovyMavenDeployer(pomFilterContainerMock, artifactPomContainerMock, loggingManagerMock, mavenSettingsProvider, mavenRepositoryLocator)

    def repositoryBuilder() {
        expect:
        checkRepositoryBuilder("repository")
    }

    def snapshotRepositoryBuilder() {
        expect:
        checkRepositoryBuilder("snapshotRepository")
    }
    
    private void checkRepositoryBuilder(String repositoryName) {
        String testUrl = 'testUrl'
        String testProxyHost = 'hans'
        String testUserName = 'userId'
        String testSnapshotUpdatePolicy = 'always'
        String testReleaseUpdatePolicy = 'never'

        groovyMavenDeployer."$repositoryName"(url: testUrl) {
            authentication(userName: testUserName)
            proxy(host: testProxyHost)
            releases(updatePolicy: testReleaseUpdatePolicy)
            snapshots(updatePolicy: testSnapshotUpdatePolicy)
        }

        assert groovyMavenDeployer."$repositoryName".url == testUrl
        assert groovyMavenDeployer."$repositoryName".authentication.userName == testUserName
        assert groovyMavenDeployer."$repositoryName".proxy.host == testProxyHost
        assert groovyMavenDeployer."$repositoryName".releases.updatePolicy == testReleaseUpdatePolicy
        assert groovyMavenDeployer."$repositoryName".snapshots.updatePolicy == testSnapshotUpdatePolicy
    }

    def filter() {
        Closure testClosure = {}
        when:
        groovyMavenDeployer.filter(testClosure)

        then:
        1 * pomFilterContainerMock.filter(testClosure)
    }

    def pom() {
        Closure testClosure = {}

        when:
        groovyMavenDeployer.pom(testClosure)

        then:
        1 * pomFilterContainerMock.pom(testClosure)
    }

    def pomWithName() {
        Closure testClosure = {}
        String testName = 'somename'

        when:
        groovyMavenDeployer.pom(testName, testClosure)

        then:
        1 * pomFilterContainerMock.pom(testName, testClosure)
    }

    def addFilter() {
        Closure testClosure = {}
        String testName = 'somename'

        when:
        groovyMavenDeployer.addFilter(testName, testClosure)

        then:
        1 * pomFilterContainerMock.addFilter(testName, testClosure)
    }
}



