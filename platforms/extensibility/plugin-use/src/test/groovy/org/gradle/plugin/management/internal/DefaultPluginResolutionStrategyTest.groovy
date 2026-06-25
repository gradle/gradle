/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.plugin.management.internal

import org.gradle.api.invocation.Gradle
import org.gradle.internal.InternalBuildAdapter
import org.gradle.internal.configuration.problems.IsolatedProjectsProblemsReporter
import org.gradle.internal.event.ListenerManager
import org.gradle.plugin.use.internal.DefaultPluginId
import spock.lang.Specification

class DefaultPluginResolutionStrategyTest extends Specification {

    def listenerManager = Mock(ListenerManager)
    def problems = Mock(IsolatedProjectsProblemsReporter)
    InternalBuildAdapter listener
    DefaultPluginResolutionStrategy strategy

    def setup() {
        // The strategy registers an InternalBuildAdapter that locks it when projects are loaded;
        // capture it so the tests can simulate that lifecycle event. Stub before constructing, since
        // the constructor registers the listener.
        listenerManager.addListener(_) >> { InternalBuildAdapter l -> listener = l }
        strategy = new DefaultPluginResolutionStrategy(listenerManager, problems)
    }

    private void lockStrategy() {
        listener.projectsLoaded(Mock(Gradle))
    }

    private PluginRequestInternal request(String id, String version = null) {
        new DefaultPluginRequest(DefaultPluginId.of(id), true, PluginRequestInternal.Origin.OTHER, "test", 1, version, null, null, null)
    }

    def "applies a default version set before projects are loaded without reporting a problem"() {
        when:
        strategy.setDefaultPluginVersion(DefaultPluginId.of("org.example"), "1.0")

        then:
        0 * problems.report(_)

        and: "the version is applied to requests without an explicit version"
        strategy.applyTo(request("org.example")).version == "1.0"
    }

    def "reports an Isolated Projects problem and ignores the version when set after projects are loaded"() {
        given:
        lockStrategy()

        when:
        strategy.setDefaultPluginVersion(DefaultPluginId.of("org.example"), "1.0")

        then:
        1 * problems.report(_)

        and: "the late version is not applied"
        strategy.applyTo(request("org.example")).version == null
    }

    def "rejects conflicting default versions for the same plugin before projects are loaded"() {
        given:
        strategy.setDefaultPluginVersion(DefaultPluginId.of("org.example"), "1.0")

        when:
        strategy.setDefaultPluginVersion(DefaultPluginId.of("org.example"), "2.0")

        then:
        def e = thrown(IllegalArgumentException)
        e.message == "Cannot provide multiple default versions for the same plugin."
        0 * problems.report(_)
    }
}
