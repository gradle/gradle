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
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.gradle.integtests.fixtures.executer.ProgressLoggingFixture
import org.gradle.test.fixtures.ivy.IvyDescriptorDependencyExclusion
import org.gradle.test.fixtures.ivy.RemoteIvyModule
import org.gradle.test.fixtures.ivy.RemoteIvyRepository
import org.gradle.test.fixtures.server.RepositoryServer
import org.gradle.test.fixtures.server.sftp.SFTPServer
import org.junit.Rule
import spock.lang.Issue

abstract class AbstractIvyRemoteLegacyPublishIntegrationTest extends AbstractIntegrationSpec {
    abstract RepositoryServer getServer()

    @Rule ProgressLoggingFixture progressLogger = new ProgressLoggingFixture(executer, temporaryFolder)

    private RemoteIvyModule module
    private RemoteIvyRepository ivyRepo

    def setup() {
        requireOwnGradleUserHomeDir()
        ivyRepo = server.remoteIvyRepo
        module = ivyRepo.module("org.gradle", "publish", "2")
    }

    @Issue("GRADLE-3440")
    @ToBeFixedForInstantExecution
    void "can publish using uploadArchives"() {
        // We expect 'The compile/runtime configuration has been deprecated for removal.' for using this legacy mechanism in the traditional way.
        executer.expectDeprecationWarning("The compile configuration has been deprecated for dependency declaration. This will fail with an error in Gradle 7.0. Please use the implementation configuration instead.")
        executer.expectDeprecationWarning("The runtime configuration has been deprecated for dependency declaration. This will fail with an error in Gradle 7.0. Please use the runtimeOnly configuration instead.")
        executer.expectDeprecationWarning("The uploadArchives task has been deprecated. This is scheduled to be removed in Gradle 7.0. Use the 'ivy-publish' plugin instead")

        given:
        settingsFile << 'rootProject.name = "publish"'
        buildFile << """
apply plugin: 'java'
version = '2'
group = 'org.gradle'

dependencies {
    compile "commons-collections:commons-collections:3.2.1"
    compile ("commons-beanutils:commons-beanutils:1.8.3") {
        exclude group: 'commons-logging'
    }
    compile ("commons-dbcp:commons-dbcp:1.4") {
       transitive = false
    }
    compile ("org.apache.camel:camel-jackson:2.15.3") {
        exclude module: 'camel-core'
    }
    runtime ("com.fasterxml.jackson.core:jackson-databind:2.2.3") {
        exclude group: 'com.fasterxml.jackson.core', module:'jackson-annotations'
        exclude group: 'com.fasterxml.jackson.core', module:'jackson-core'
    }
    runtime "commons-io:commons-io:1.4"
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
        module.parsedIvy.expectArtifact("publish", "jar").hasAttributes("jar", "jar", ["apiElements", "archives", "runtime", "runtimeElements"], null)

        with (module.parsedIvy) {
            dependencies.size() == 6
            dependencies["commons-collections:commons-collections:3.2.1"].hasConf("compile->default")
            dependencies["commons-beanutils:commons-beanutils:1.8.3"].hasConf("compile->default")
            dependencies["commons-dbcp:commons-dbcp:1.4"].hasConf("compile->default")
            dependencies["com.fasterxml.jackson.core:jackson-databind:2.2.3"].hasConf("runtime->default")
            dependencies["commons-io:commons-io:1.4"].hasConf("runtime->default")

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

    @ToBeFixedForInstantExecution
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
        executer.expectDeprecationWarning("The uploadArchives task has been deprecated. This is scheduled to be removed in Gradle 7.0. Use the 'ivy-publish' plugin instead")
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
