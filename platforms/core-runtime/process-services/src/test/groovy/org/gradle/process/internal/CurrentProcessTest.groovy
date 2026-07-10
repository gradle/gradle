/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.process.internal

import org.gradle.api.internal.file.FileCollectionFactory
import org.gradle.internal.instrumentation.agent.AgentUtils
import org.gradle.internal.jvm.Jvm
import spock.lang.Specification

import static org.gradle.process.internal.CurrentProcess.inferJvmOptions

class CurrentProcessTest extends Specification {
    def "default JVM is the current process JVM"() {
        def currentProcess = new CurrentProcess(Mock(FileCollectionFactory))
        expect:
        currentProcess.jvm == Jvm.current()
    }

    def "agent arguments are ignored"() {
        def options = inferJvmOptions(Mock(FileCollectionFactory), ['-Xmx1g', '-javaagent:foo.jar', '-javaagent:' + AgentUtils.AGENT_MODULE_NAME + '.jar'])
        expect:
        options.allJvmArgs.containsAll('-Xmx1g', '-javaagent:foo.jar')
        !options.allJvmArgs.contains('-javaagent:' + AgentUtils.AGENT_MODULE_NAME + '.jar')
    }
}
