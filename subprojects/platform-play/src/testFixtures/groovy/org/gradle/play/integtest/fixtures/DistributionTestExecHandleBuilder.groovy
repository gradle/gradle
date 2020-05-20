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

import org.apache.commons.io.output.CloseShieldOutputStream
import org.apache.commons.io.output.TeeOutputStream
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.os.OperatingSystem
import org.gradle.process.internal.ExecHandle
import org.gradle.process.internal.ExecHandleBuilder
import org.gradle.test.fixtures.ConcurrentTestUtil

class DistributionTestExecHandleBuilder {
    final String port
    @Delegate
    ExecHandleBuilder builder = TestFiles.execHandleFactory().newExec()

    DistributionTestExecHandleBuilder(String port, String baseDirName) {
        this.port = port

        def extension = ""
        if (OperatingSystem.current().windows) {
            extension = ".bat"
        }

        this.setExecutable("${baseDirName}/playBinary/bin/playBinary${extension}")
        this.environment("PLAY_BINARY_OPTS": "-Dhttp.port=${port}")
        this.environment("JAVA_HOME", System.getProperty("java.home"))
        this.setWorkingDir(baseDirName)
    }

    @Override
    ExecHandle build() {
        ByteArrayOutputStream stdout = new ByteArrayOutputStream()
        ByteArrayOutputStream errorOutput = new ByteArrayOutputStream()
        this.setStandardOutput(new CloseShieldOutputStream(new TeeOutputStream(System.out, stdout)));
        this.setErrorOutput(new CloseShieldOutputStream(new TeeOutputStream(System.err, errorOutput)));
        return new DistributionTestExecHandle(builder.build(), stdout, errorOutput)
    }

    static class DistributionTestExecHandle implements ExecHandle {
        final private ByteArrayOutputStream standardOutput
        final private ByteArrayOutputStream errorOutput

        @Delegate
        final ExecHandle delegate

        public DistributionTestExecHandle(ExecHandle delegate, ByteArrayOutputStream standardOutput, ByteArrayOutputStream errorOutput) {
            this.delegate = delegate
            this.standardOutput = standardOutput
            this.errorOutput = errorOutput
        }

        void shutdown(int port) {
            try {
                stop(port)
                waitForFinish()
            } finally {
                try {
                    abort()
                } catch (IllegalStateException e) {
                    // Ignore if process is already not running
                    println "Did not abort play process since current state is: ${state.toString()}"
                }
            }
        }

        private stop(int port) {
            try {
                new URL("http://localhost:${port}/shutdown").bytes
            } catch (SocketException e) {
                // Expected
            }

            ConcurrentTestUtil.poll(30) {
                try {
                    def connection = new URL("http://localhost:${port}/").openConnection()
                    connection.connect()
                    // we can still connect to the application
                    assert false: "Waiting for application to finally die"
                } catch (IOException e) {
                    // Application is dead
                }
            }
        }

        ByteArrayOutputStream getStandardOutput() {
            return standardOutput
        }

        ByteArrayOutputStream getErrorOutput() {
            return errorOutput
        }
    }
}
