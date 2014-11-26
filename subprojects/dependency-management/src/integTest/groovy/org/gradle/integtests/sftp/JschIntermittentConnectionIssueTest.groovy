/*
 * Copyright 2014 the original author or authors.
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



package org.gradle.integtests.sftp

import org.gradle.internal.resource.PasswordCredentials
import org.gradle.internal.resource.transport.sftp.SftpClientFactory
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.test.fixtures.server.sftp.SFTPServer
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.Unroll

@Ignore
class JschIntermittentConnectionIssueTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider provider = new TestNameTestDirectoryProvider()

    @Rule
    final SFTPServer server = new SFTPServer(provider)

    @Unroll
    def "check"() {
        expect:
        new SftpClientFactory().createSftpClient(server.uri, new PasswordCredentials("test", "test"))

        where:
        runs << (1..10000)
    }
}
