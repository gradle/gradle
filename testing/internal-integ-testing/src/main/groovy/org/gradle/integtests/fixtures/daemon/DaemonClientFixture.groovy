/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.integtests.fixtures.daemon

import org.gradle.integtests.fixtures.ProcessFixture
import org.gradle.integtests.fixtures.executer.GradleHandle
import org.gradle.test.fixtures.ConcurrentTestUtil


class DaemonClientFixture {
    final GradleHandle gradleHandle
    final ProcessFixture process

    DaemonClientFixture(GradleHandle gradleHandle) {
        this.gradleHandle = gradleHandle
        Long pid
        ConcurrentTestUtil.poll {
            pid = getPidFromOutput(gradleHandle) as Long
        }
        this.process = new ProcessFixture(pid)
    }

    DaemonClientFixture kill() {
        process.kill(false)
        gradleHandle.waitForFailure()
        return this
    }

    static String getPidFromOutput(GradleHandle build) {
        def matcher = (build.standardOutput =~ /Executing build [^\s]+ in daemon client \{pid=(\d+)\}/)
        if (matcher.size() > 0) {
            return matcher[0][1]
        } else {
            throw new IllegalStateException("Could not infer pid from test output")
        }
    }
}
