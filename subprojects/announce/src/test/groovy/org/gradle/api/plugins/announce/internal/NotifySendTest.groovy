/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.plugins.announce.internal

import org.gradle.api.Project
import spock.lang.Specification
import org.gradle.util.ConfigureUtil
import org.gradle.process.internal.ExecHandleBuilder
import org.gradle.process.ExecResult

class NotifySendTest extends Specification {
    def "sending of an announcement invokes notify-send command"() {
        def execClosure
        def project = Mock(Project)
        def notifier = new NotifySend(project)

        when:
        notifier.send("title", "body")

        then:
        1 * project.exec(!null) >> { execClosure = it[0]; Mock(ExecResult) }
        def execSpec = ConfigureUtil.configure(execClosure, new ExecHandleBuilder())
        execSpec.executable == 'notify-send'
        execSpec.args.contains 'title'
        execSpec.args.contains 'body'
    }
}
