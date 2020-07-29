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
package org.gradle.launcher.daemon.client

import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.logging.services.LoggingServiceRegistry
import org.gradle.internal.service.ServiceRegistryBuilder
import org.gradle.internal.service.scopes.BasicGlobalScopeServices
import org.gradle.launcher.configuration.BuildLayoutResult
import org.gradle.launcher.daemon.configuration.DaemonParameters
import org.gradle.launcher.daemon.context.DaemonContext
import org.gradle.launcher.daemon.registry.DaemonRegistry
import org.gradle.launcher.daemon.registry.PersistentDaemonRegistry
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.testfixtures.internal.NativeServicesTestFixture
import org.junit.Rule
import spock.lang.Specification

class DaemonClientServicesTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider tmp = new TestNameTestDirectoryProvider(getClass())
    def buildLayoutResult = Stub(BuildLayoutResult) {
        getGradleUserHomeDir() >> tmp.file("gradle-user-home")
    }
    final DaemonParameters parameters = new DaemonParameters(buildLayoutResult, TestFiles.fileCollectionFactory()).setBaseDir(tmp.testDirectory)
    final parentServices = ServiceRegistryBuilder.builder()
        .parent(LoggingServiceRegistry.newEmbeddableLogging())
        .parent(NativeServicesTestFixture.instance)
        .provider(new BasicGlobalScopeServices())
        .provider(new DaemonClientGlobalServices())
        .build()
    final services = new DaemonClientServices(parentServices, parameters, System.in)

    def "makes a DaemonRegistry available"() {
        expect:
        services.get(DaemonRegistry.class) instanceof PersistentDaemonRegistry
    }

    def "makes a DaemonConnector available"() {
        expect:
        services.get(DaemonConnector.class) instanceof DefaultDaemonConnector
    }

    def "makes a DaemonClient available"() {
        expect:
        services.get(DaemonClient) != null
    }

    def "context includes locale"() {
        expect:
        services.get(DaemonContext).daemonOpts.contains("-Duser.language=${Locale.default.language}".toString())
    }

}
