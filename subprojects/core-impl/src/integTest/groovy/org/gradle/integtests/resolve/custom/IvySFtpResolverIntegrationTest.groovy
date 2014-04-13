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
package org.gradle.integtests.resolve.custom

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.ProgressLoggingFixture
import org.gradle.test.fixtures.ivy.IvySftpRepository
import org.gradle.test.fixtures.server.sftp.SFTPServer
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Rule

@Requires(TestPrecondition.JDK6_OR_LATER)
class IvySFtpResolverIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    public final SFTPServer server = new SFTPServer(this)

    @Rule ProgressLoggingFixture progressLogging = new ProgressLoggingFixture(executer, temporaryFolder)

    def setup() {
        requireOwnGradleUserHomeDir()
    }

    public void "can resolve and cache dependencies from an SFTP Ivy repository"() {
        given:
        def repo = ivyRepo()
        def module = repo.module('group', 'projectA', '1.2')
        module.publish();

        and:
        buildFile << """
repositories {
    add(new org.apache.ivy.plugins.resolver.SFTPResolver()) {
        name = "sftprepo"
        host = "${server.hostAddress}"
        port = ${server.port}
        user = "simple"
        userPassword = "simple"
        addIvyPattern "repos/libs/[organization]/[module]/[revision]/ivy-[revision].xml"
        addArtifactPattern "repos/libs/[organisation]/[module]/[revision]/[artifact]-[revision].[ext]"
    }
}
configurations { compile }
dependencies { compile 'group:projectA:1.2' }
task listJars << {
    assert configurations.compile.collect { it.name } == ['projectA-1.2.jar']
}
"""
        when:
        server.expectInit()
        server.expectRealpath('')

        module.ivy.expectStat()

        server.expectOpendir('/repos/libs/group/projectA/1.2')
        server.allowReaddir('/repos/libs/group/projectA/1.2')
        server.expectClose('/repos/libs/group/projectA/1.2')

        module.ivy.expectStat()
        module.ivy.expectOpen()
        module.ivy.allowRead()
        module.ivy.expectClose()

        module.jar.expectStat()

        server.expectOpendir('/repos/libs/group/projectA/1.2')
        server.allowReaddir('/repos/libs/group/projectA/1.2')
        server.expectClose('/repos/libs/group/projectA/1.2')

        module.jar.expectStat()
        module.jar.expectOpen()
        module.jar.allowRead()
        module.jar.expectClose()

        and:
        executer.withDeprecationChecksDisabled()
        run 'listJars'

        then:
        progressLogging.downloadProgressLogged("repos/libs/group/projectA/1.2/ivy-1.2.xml")
        progressLogging.downloadProgressLogged("repos/libs/group/projectA/1.2/projectA-1.2.jar")

        when:
        server.resetExpectations()
        executer.withDeprecationChecksDisabled()

        then:
        run 'listJars'
    }

    IvySftpRepository ivyRepo() {
        return new IvySftpRepository(server, '/repos/libs')
    }
}