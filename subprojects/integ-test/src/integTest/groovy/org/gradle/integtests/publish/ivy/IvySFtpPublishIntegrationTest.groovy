/*
 * Copyright 2012 the original author or authors.
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
package org.gradle.integtests.publish.ivy

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ProgressLoggingFixture
import org.gradle.test.fixtures.server.sftp.SFTPServer
import org.junit.Rule

class IvySFtpPublishIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final SFTPServer sftpServer = new SFTPServer(this)
    @Rule
    ProgressLoggingFixture progressLogging

    public void "can publish using SftpResolver"() {
        given:
        file("settings.gradle") << 'rootProject.name = "publish"'

        and:
        buildFile << """
        apply plugin: 'java'
        version = '2'
        group = 'org.gradle'
        uploadArchives {
            repositories {
                add(new org.apache.ivy.plugins.resolver.SFTPResolver()) {
                    addArtifactPattern "repos/libs/[organisation]/[module]/[artifact]-[revision].[ext]"
                    host = "${sftpServer.hostAddress}"
                    port = ${sftpServer.port}
                    user = "user"
                    userPassword = "user"
                }
            }
        }
        """
        when:

        run "uploadArchives"
        then:
        sftpServer.hasFile("repos/libs/org.gradle/publish/publish-2.jar")
        sftpServer.hasFile("repos/libs/org.gradle/publish/ivy-2.xml");
        sftpServer.file("repos/libs/org.gradle/publish/publish-2.jar").assertIsCopyOf(file('build/libs/publish-2.jar'))
        and:
        progressLogging.uploadProgressLogged("repos/libs/org.gradle/publish/ivy-2.xml")
        progressLogging.uploadProgressLogged("repos/libs/org.gradle/publish/publish-2.jar")
    }

    public void "reports Authentication Errors"() {
        given:
        file("settings.gradle") << 'rootProject.name = "publish"'

        and:
        buildFile << """
        apply plugin: 'java'
        version = '2'
        group = 'org.gradle'
        uploadArchives {
            repositories {
                add(new org.apache.ivy.plugins.resolver.SFTPResolver()) {
                    addArtifactPattern "repos/libs/[organisation]/[module]/[artifact]-[revision].[ext]"
                    host = "${sftpServer.hostAddress}"
                    port = ${sftpServer.port}
                    user = "simple"
                    userPassword = "wrongPassword"
                }
            }
        }
        """
        when:
        fails "uploadArchives"

        then:
        failure.assertHasDescription('Execution failed for task \':uploadArchives\'.')
        failure.assertHasCause('Could not publish configuration \'archives\'')
        failure.assertHasCause("java.io.IOException: Auth fail")
    }
}
