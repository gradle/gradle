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

package org.gradle.api.plugins.quality.internal.findbugs

import spock.lang.Specification
import spock.lang.Subject

class FindBugsWorkerServerTest extends Specification {

    def executer = Mock(FindBugsExecuter)
    def spec = Stub(FindBugsSpec)
    @Subject server = new FindBugsWorkerServer(spec)

    def "fatal crash provides result"() {
        def error = new Error("Ka-boom!")

        when:
        def r = server.execute(executer)

        then:
        1 * executer.runFindbugs(spec) >> { throw error }

        and:
        r.exception == error
    }
}
