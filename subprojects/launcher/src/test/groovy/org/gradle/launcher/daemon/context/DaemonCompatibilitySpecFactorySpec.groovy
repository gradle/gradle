/*
 * Copyright 2011 the original author or authors.
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

import spock.lang.*
import org.gradle.util.ConfigureUtil

class DaemonCompatibilitySpecFactorySpec extends Specification {

    def clientConfigure = {}
    def serverConfigure = {}

    def client(Closure c) {
        clientConfigure = c
    }

    def server(Closure c) {
        serverConfigure = c
    }

    def createContext(Closure config) {
        ConfigureUtil.configure(config, new DaemonContextBuilder()).create()
    }

    def getClientContext() {
        createContext(clientConfigure)
    }

    def getServerContext() {
        createContext(serverConfigure)
    }

    boolean isIsCompatible() {
        new DaemonCompatibilitySpecFactory(clientContext).create().isSatisfiedBy(serverContext)
    }

    boolean isIsNotCompatible() {
        !isCompatible
    }

    def "identical contexts are compatible"() {
        expect:
        isCompatible
    }

    def "contexts with different java homes are incompatible"() {
        when:
        client { javaHome = new File("a") }
        server { javaHome = new File("b") }

        then:
        isNotCompatible
    }

}