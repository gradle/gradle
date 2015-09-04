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
package org.gradle.launcher.daemon.server

import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.nativeintegration.ProcessEnvironment
import org.gradle.launcher.daemon.configuration.DefaultDaemonServerConfiguration
import org.gradle.launcher.daemon.registry.DaemonDir
import org.gradle.logging.LoggingManagerInternal
import org.gradle.logging.LoggingServiceRegistry
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import spock.lang.Specification

import static java.util.Arrays.asList

@UsesNativeServices
class DaemonServicesTest extends Specification {
    @Rule TestNameTestDirectoryProvider tmp = new TestNameTestDirectoryProvider()
    final DaemonServices services = new DaemonServices(new DefaultDaemonServerConfiguration("uid", tmp.testDirectory, 100, asList()),
            LoggingServiceRegistry.newEmbeddableLogging(), Mock(LoggingManagerInternal), Stub(ClassPath))

    def "makes a DaemonDir available"() {
        expect:
        services.get(DaemonDir.class) != null
    }

    def "makes a ProcessEnvironment available"() {
        expect:
        services.get(ProcessEnvironment.class) != null
    }

    def "makes a Daemon available"() {
        expect:
        services.get(Daemon.class) != null
    }
}
