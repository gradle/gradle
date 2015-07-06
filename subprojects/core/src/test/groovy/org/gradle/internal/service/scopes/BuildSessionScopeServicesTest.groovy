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

package org.gradle.internal.service.scopes

import org.gradle.initialization.DefaultGradleLauncherFactory
import org.gradle.initialization.GradleLauncherFactory
import org.gradle.internal.event.ListenerManager
import org.gradle.internal.service.ServiceRegistry
import org.gradle.logging.ProgressLoggerFactory
import spock.lang.Specification


class BuildSessionScopeServicesTest extends Specification {
    ServiceRegistry parent = Stub()
    BuildSessionScopeServices services = new BuildSessionScopeServices(parent)

    def setup() {
        parent.get(ListenerManager) >> Stub(ListenerManager)
        parent.get(ProgressLoggerFactory) >> Stub(ProgressLoggerFactory)
    }

    def "provides a GradleLauncherFactory" () {
        expect:
        services.get(GradleLauncherFactory) instanceof DefaultGradleLauncherFactory
    }
}
