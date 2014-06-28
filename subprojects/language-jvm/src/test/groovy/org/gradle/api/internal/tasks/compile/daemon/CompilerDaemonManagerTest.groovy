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

package org.gradle.api.internal.tasks.compile.daemon

import org.gradle.language.base.internal.compile.CompileSpec
import org.gradle.language.base.internal.compile.Compiler
import spock.lang.Specification
import spock.lang.Subject

class CompilerDaemonManagerTest extends Specification {

    def clientsManager = Mock(CompilerClientsManager)
    def client = Mock(CompilerDaemonClient)

    @Subject manager = new CompilerDaemonManager(clientsManager)

    def workingDir = new File("some-dir")
    def compiler = Stub(Compiler)
    def options = Stub(DaemonForkOptions)
    def compileSpec = Stub(CompileSpec)

    def "getting a compiler daemon does not assume client use"() {
        when:
        manager.getDaemon(workingDir, options);

        then:
        0 * clientsManager._
    }

    def "new client is created when daemon is executed and no idle clients found"() {
        when:
        manager.getDaemon(workingDir, options).execute(compiler, compileSpec)

        then:
        1 * clientsManager.reserveIdleClient(options) >> null

        then:
        1 * clientsManager.reserveNewClient(workingDir, options) >> client

        then:
        1 * client.execute(compiler, compileSpec)

        then:
        1 * clientsManager.release(client)
        0 * _._
    }

    def "idle client is reused when daemon is executed"() {
        when:
        manager.getDaemon(workingDir, options).execute(compiler, compileSpec)

        then:
        1 * clientsManager.reserveIdleClient(options) >> client

        then:
        1 * client.execute(compiler, compileSpec)

        then:
        1 * clientsManager.release(client)
        0 * _._
    }

    def "client is released even if execution fails"() {
        when:
        manager.getDaemon(workingDir, options).execute(compiler, compileSpec)

        then:
        1 * clientsManager.reserveIdleClient(options) >> client

        then:
        1 * client.execute(compiler, compileSpec) >> { throw new RuntimeException("Boo!") }

        then:
        thrown(RuntimeException)
        1 * clientsManager.release(client)
        0 * _._
    }

    def "stops clients"() {
        when:
        manager.stop()

        then:
        clientsManager.stop()
    }
}
