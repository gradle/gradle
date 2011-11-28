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

import spock.lang.Specification
import org.gradle.launcher.daemon.registry.DaemonRegistry
import org.gradle.launcher.daemon.registry.PersistentDaemonRegistry
import org.gradle.logging.LoggingServiceRegistry

class DaemonClientServicesTest extends Specification {
    final DaemonClientServices services = new DaemonClientServices(LoggingServiceRegistry.newEmbeddableLogging(), new File("user-home"), [], 100)

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
}
