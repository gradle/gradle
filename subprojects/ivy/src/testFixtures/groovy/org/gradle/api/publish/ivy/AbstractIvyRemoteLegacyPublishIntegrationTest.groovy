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


package org.gradle.api.publish.ivy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.ToBeFixedForConfigurationCache
import org.gradle.integtests.fixtures.executer.ProgressLoggingFixture
import org.gradle.test.fixtures.ivy.IvyDescriptorDependencyExclusion
import org.gradle.test.fixtures.ivy.RemoteIvyModule
import org.gradle.test.fixtures.ivy.RemoteIvyRepository
import org.gradle.test.fixtures.server.RepositoryServer
import org.gradle.test.fixtures.server.sftp.SFTPServer
import org.junit.Rule
import spock.lang.Issue

abstract class AbstractIvyRemoteLegacyPublishIntegrationTest extends AbstractIntegrationSpec implements WithUploadArchives {
    abstract RepositoryServer getServer()

    @Rule
    ProgressLoggingFixture progressLogger = new ProgressLoggingFixture(executer, temporaryFolder)

    private RemoteIvyModule module
    private RemoteIvyRepository ivyRepo

    def setup() {
        requireOwnGradleUserHomeDir()
        ivyRepo = server.remoteIvyRepo
        module = ivyRepo.module("org.gradle", "publish", "2")
        configureUploadTask()
        executer.expectDocumentedDeprecationWarning("The task type org.gradle.api.tasks.Upload (used by the :uploadArchives task) has been deprecated. This is scheduled to be removed in Gradle 8.0. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_7.html#upload_task_deprecation")
    }

    @Issue("GRADLE-3440")
    @ToBeFixedForConfigurationCache(
        skip = ToBeFixedForConfigurationCache.Skip.FAILS_TO_CLEANUP,
        because = "IvyGcsUploadArchivesIntegrationTest leaks test files"
    )
    void "can publish using uploadArchives"() {
        given:
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
apply plugin: 'java'
version = '2'
group = 'org.gradle'

dependencies {
    implementation "commons-collections:commons-collections:3.2.1"
    implementation ("commons-beanutils:commons-beanutils:1.8.3") {
        exclude group: 'commons-logging'
    }
    implementation ("commons-dbcp:commons-dbcp:1.4") {
       transitive = false
    }
    implementation ("org.apache.camel:camel-jackson:2.15.3") {
        exclude module: 'camel-core'
    }
    runtimeOnly ("com.fasterxml.jackson.core:jackson-databind:2.2.3") {
        exclude group: 'com.fasterxml.jackson.core', module:'jackson-annotations'
        exclude group: 'com.fasterxml.jackson.core', module:'jackson-core'
    }
    runtimeOnly "commons-io:commons-io:1.4"
}

uploadArchives {
    repositories {
        ivy {
            url "${ivyRepo.uri}"
            ${server.validCredentials}
        }
    }
}
"""
        and:
        module.jar.expectParentMkdir()
        module.jar.expectUpload()
        // TODO - should not check on each upload to a particular directory
        module.jar.sha1.expectParentCheckdir()
        module.jar.sha1.expectUpload()
        module.jar.sha256.expectParentCheckdir()
        module.jar.sha256.expectUpload()
        module.jar.sha512.expectParentCheckdir()
        module.jar.sha512.expectUpload()
        module.ivy.expectParentCheckdir()
        module.ivy.expectUpload()
        module.ivy.sha1.expectParentCheckdir()
        module.ivy.sha1.expectUpload()
        module.ivy.sha256.expectParentCheckdir()
        module.ivy.sha256.expectUpload()
        module.ivy.sha512.expectParentCheckdir()
        module.ivy.sha512.expectUpload()

        when:
        run 'uploadArchives'

        then:
        module.assertIvyAndJarFilePublished()
        module.jarFile.assertIsCopyOf(file('build/libs/publish-2.jar'))
        module.parsedIvy.expectArtifact("publish", "jar").hasAttributes("jar", "jar", ["apiElements", "archives", "runtimeElements"], null)

        with(module.parsedIvy) {
            dependencies.size() == 6
            dependencies["commons-collections:commons-collections:3.2.1"].hasConf("implementation->default")
            dependencies["commons-beanutils:commons-beanutils:1.8.3"].hasConf("implementation->default")
            dependencies["commons-dbcp:commons-dbcp:1.4"].hasConf("implementation->default")
            dependencies["com.fasterxml.jackson.core:jackson-databind:2.2.3"].hasConf("runtimeOnly->default")
            dependencies["commons-io:commons-io:1.4"].hasConf("runtimeOnly->default")

            dependencies["commons-beanutils:commons-beanutils:1.8.3"].hasExclude(new IvyDescriptorDependencyExclusion(org: 'commons-logging', module: '*'))
            dependencies["com.fasterxml.jackson.core:jackson-databind:2.2.3"].hasExclude(new IvyDescriptorDependencyExclusion(org: 'com.fasterxml.jackson.core', module: 'jackson-annotations'))
            dependencies["com.fasterxml.jackson.core:jackson-databind:2.2.3"].hasExclude(new IvyDescriptorDependencyExclusion(org: 'com.fasterxml.jackson.core', module: 'jackson-core'))
            dependencies["org.apache.camel:camel-jackson:2.15.3"].hasExclude(new IvyDescriptorDependencyExclusion(org: '*', module: 'camel-core'))

            dependencies["commons-beanutils:commons-beanutils:1.8.3"].transitiveEnabled()
            dependencies["com.fasterxml.jackson.core:jackson-databind:2.2.3"].transitiveEnabled()
            dependencies["org.apache.camel:camel-jackson:2.15.3"].transitiveEnabled()
            !dependencies["commons-dbcp:commons-dbcp:1.4"].transitiveEnabled()
        }

        and:
        progressLogger.uploadProgressLogged(module.jar.uri)
        progressLogger.uploadProgressLogged(module.ivy.uri)
    }

    @ToBeFixedForConfigurationCache(
        skip = ToBeFixedForConfigurationCache.Skip.FAILS_TO_CLEANUP,
        because = "IvyGcsUploadArchivesIntegrationTest leaks test files"
    )
    void "does not upload meta-data file when artifact upload fails"() {
        given:
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
apply plugin: 'java'
version = '2'
group = 'org.gradle'
uploadArchives {
    repositories {
        ivy {
            url "${ivyRepo.uri}"
            ${server.validCredentials}
        }
    }
}
"""
        and:
        module.jar.expectParentMkdir()
        module.jar.expectUploadBroken()

        when:
        fails 'uploadArchives'

        then:
        module.ivyFile.assertDoesNotExist()

        and:
        progressLogger.uploadProgressLogged(module.jar.uri)

        cleanup:
        if (server instanceof SFTPServer) {
            ((SFTPServer) server).clearSessions()
        }
    }
}
