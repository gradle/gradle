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

package org.gradle.process.internal.daemon

import org.gradle.api.internal.file.FileResolver
import org.gradle.internal.Factory
import org.gradle.util.UsesNativeServices
import spock.lang.Specification

@UsesNativeServices
class AbstractWorkerDaemonExecutorTest extends Specification {
    def workerDaemonFactory = Mock(WorkerDaemonFactory)
    def fileResolver = Mock(FileResolver)
    def factory = Mock(Factory)
    def actionImpl = Mock(Serializable)
    def serverImpl = Mock(WorkerDaemonProtocol)
    TestExecutor testExecutor

    def setup() {
        _ * fileResolver.resolveLater(_) >> factory
        _ * fileResolver.resolve(_) >> { files -> files[0] }
        testExecutor = new TestExecutor(workerDaemonFactory, fileResolver, actionImpl.class, serverImpl.class)
    }

    def "can convert javaForkOptions to daemonForkOptions"() {
        given:
        testExecutor.forkOptions { options ->
            options.minHeapSize = "128m"
            options.maxHeapSize = "128m"
            options.systemProperty("foo", "bar")
            options.jvmArgs("-foo")
            options.bootstrapClasspath(new File("/foo"))
            options.debug = true
        }

        when:
        DaemonForkOptions daemonForkOptions = testExecutor.getDaemonForkOptions()

        then:
        daemonForkOptions.minHeapSize == "128m"
        daemonForkOptions.maxHeapSize == "128m"
        daemonForkOptions.jvmArgs.contains("-Dfoo=bar")
        daemonForkOptions.jvmArgs.contains("-foo")
        daemonForkOptions.jvmArgs.contains("-Xbootclasspath:${File.separator}foo".toString())
        daemonForkOptions.jvmArgs.contains("-agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=5005")
    }

    def "can add to classpath on executor"() {
        given:
        def foo = new File("/foo")
        testExecutor.classpath([foo])

        when:
        DaemonForkOptions daemonForkOptions = testExecutor.getDaemonForkOptions()

        then:
        daemonForkOptions.classpath.contains(foo)
    }

    private static class TestExecutor extends AbstractWorkerDaemonExecutor<Serializable> {
        TestExecutor(WorkerDaemonFactory workerDaemonFactory, FileResolver fileResolver, Class<? extends Serializable> implementationClass, Class<? extends WorkerDaemonProtocol> serverImplementationClass) {
            super(workerDaemonFactory, fileResolver, implementationClass, serverImplementationClass)
        }

        @Override
        def WorkSpec getSpec() {
            return null
        }

        @Override
        def WorkerDaemonAction getAction() {
            return null
        }

        public Set<File> getClasspath() {
            return super.getClasspath();
        }

        public Set<String> getSharedPackages() {
            return super.getSharedPackages();
        }

        public Class<? extends Serializable> getImplementationClass() {
            return super.getImplementationClass();
        }

        public Serializable[] getParams() {
            return super.getParams();
        }

        public Class<? extends WorkerDaemonProtocol> getServerImplementationClass() {
            return super.getServerImplementationClass();
        }

        public DaemonForkOptions getDaemonForkOptions() {
            return super.getDaemonForkOptions()
        }
    }
}
