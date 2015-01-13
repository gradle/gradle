/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.play.integtest.fixtures

import com.google.common.collect.Lists
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.internal.ExecHandle
import org.gradle.process.internal.ExecHandleBuilder

class DistributionTestExecHandleBuilder extends ExecHandleBuilder {
    final String port

    DistributionTestExecHandleBuilder(String port, String baseDirName) {
        super()
        this.port = port

        def extension = ""
        if (OperatingSystem.current().windows) {
            extension = ".bat"
        }

        this.setExecutable("${baseDirName}/playBinary/bin/playBinary${extension}")
        this.environment("PLAY_BINARY_OPTS": "-Dhttp.port=${port}")
        this.setWorkingDir(baseDirName)
    }

    @Override
    List<String> getAllArguments() {
        return Lists.newArrayList()
    }

    @Override
    ExecHandle build() {
        return new DistributionTestExecHandle(super.build(), port)
    }

    public static class DistributionTestExecHandle implements ExecHandle {
        @Delegate
        final ExecHandle delegate
        final String port

        public DistributionTestExecHandle(ExecHandle delegate, String port) {
            this.delegate = delegate
            this.port = port
        }

        void shutdown() {
            try {
                new URL("http://localhost:${port}/shutdown").bytes
            } catch (SocketException e) {
                // Expected
            }

            try {
                abort()
            } catch (IllegalStateException e) {
                // Ignore if process is already not running
                println "Did not abort play process since current state is: ${state.toString()}"
            }
        }
    }
}
