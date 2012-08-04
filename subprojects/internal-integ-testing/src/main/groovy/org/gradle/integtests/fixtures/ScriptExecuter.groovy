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
package org.gradle.integtests.fixtures

import org.gradle.internal.os.OperatingSystem
import org.gradle.process.ExecResult
import org.gradle.process.internal.ExecHandle
import org.gradle.process.internal.ExecHandleBuilder

class ScriptExecuter extends ExecHandleBuilder {
    @Override
    ExecHandle build() {
        if (OperatingSystem.current().isWindows()) {
            def theArgs = ['/c', executable.replace('/', File.separator)] + getArgs()
            setArgs(theArgs) //split purposefully to avoid weird windows CI issue
            executable = 'cmd'
        } else {
            executable = "${workingDir}/${executable}"
        }
        return super.build()
    }

    ExecResult run() {
        return build().start().waitForFinish()
    }
}
