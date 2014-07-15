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
package org.gradle.launcher.daemon.context

import org.gradle.internal.nativeplatform.ProcessEnvironment
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.ConfigureUtil
import org.junit.Rule
import spock.lang.Specification

class DaemonUidCompatibilitySpecSpec extends Specification {

    @Rule TestNameTestDirectoryProvider tmp = new TestNameTestDirectoryProvider()

    def clientConfigure = {}
    def serverConfigure = {}

    def client(Closure c) {
        clientConfigure = c
    }

    def server(Closure c) {
        serverConfigure = c
    }

    def createContext(Closure config) {
        def builder = new DaemonContextBuilder({ 12L } as ProcessEnvironment)
        builder.daemonRegistryDir = new File("dir")
        builder.uid = 'a'
        ConfigureUtil.configure(config, builder).create()
    }

    def getClientContext() {
        createContext(clientConfigure)
    }

    def getServerContext() {
        createContext(serverConfigure)
    }

    private requiringSpec() {
        DaemonUidCompatibilitySpec.createSpecRequiringUid(new DaemonCompatibilitySpec(clientContext), 'a')
    }

    private rejectingSpec() {
        DaemonUidCompatibilitySpec.createSpecRejectingUids(new DaemonCompatibilitySpec(clientContext), ['a'])
    }

    def "default contexts are compatible"() {
        expect:
        requiringSpec().isSatisfiedBy(serverContext)
        !requiringSpec().whyUnsatisfied(serverContext)

        !rejectingSpec().isSatisfiedBy(serverContext)
        rejectingSpec().whyUnsatisfied(serverContext).contains "Different daemon instance"
    }

    def "contexts with different uids incompatible"() {
        server { uid = 'b' }

        expect:
        !requiringSpec().isSatisfiedBy(serverContext)
        requiringSpec().whyUnsatisfied(serverContext).contains "Different daemon instance"

        rejectingSpec().isSatisfiedBy(serverContext)
        !rejectingSpec().whyUnsatisfied(serverContext)
    }
}