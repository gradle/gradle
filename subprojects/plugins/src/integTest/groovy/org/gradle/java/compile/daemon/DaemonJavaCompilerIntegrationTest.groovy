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
package org.gradle.java.compile.daemon

import org.gradle.java.compile.JavaCompilerIntegrationSpec

class DaemonJavaCompilerIntegrationTest extends JavaCompilerIntegrationSpec {

    def "respects fork options settings"() {
        goodCode()
        buildFile << """
            import org.gradle.workers.internal.WorkerDaemonClientsManager
            import org.gradle.internal.jvm.Jvm
            
            tasks.withType(JavaCompile) { 
                options.forkOptions.memoryInitialSize = "128m"
                options.forkOptions.memoryMaximumSize = "256m"
                options.forkOptions.jvmArgs = ["-Dfoo=bar"]
                
                doLast {
                    assert services.get(WorkerDaemonClientsManager).idleClients.find { 
                        new File(it.forkOptions.javaForkOptions.executable).canonicalPath == Jvm.current().javaExecutable.canonicalPath &&
                        it.forkOptions.javaForkOptions.minHeapSize == "128m" &&
                        it.forkOptions.javaForkOptions.maxHeapSize == "256m" &&
                        it.forkOptions.javaForkOptions.systemProperties['foo'] == "bar"
                    }
                }
            }
        """

        expect:
        succeeds "compileJava"
    }

    def setup() {
        executer.withArguments("-d")
    }

    def compilerConfiguration() {
        "tasks.withType(JavaCompile) { options.fork = true }"
    }

    def logStatement() {
        "worker(s) in use"
    }
}
