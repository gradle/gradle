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

package org.gradle.launcher.daemon

import org.gradle.launcher.daemon.bootstrap.DaemonOutputConsumer
import org.gradle.process.internal.ExecHandleBuilder
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

@UsesNativeServices
class DaemonExecHandleBuilderSpec extends Specification {
    def builder = Mock(ExecHandleBuilder)
    def daemonBuilder = new DaemonExecHandleBuilder(builder: builder)

    def "creates process with correct settings"() {
        when:
        daemonBuilder.build(["java", "-cp"], new File("foo"), Mock(DaemonOutputConsumer))

        then:
        //integ test coverage for certain below settings is either not easy or not obvious
        1 * builder.setTimeout(30000)
        1 * builder.redirectErrorStream()
        1 * builder.setWorkingDir(new File("foo"))
        1 * builder.commandLine(["java", "-cp"])
    }
}
