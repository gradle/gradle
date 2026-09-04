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

import org.gradle.api.artifacts.MutableVersionConstraint
import org.gradle.api.internal.artifacts.dependencies.DefaultMutableVersionConstraint
import org.gradle.api.invocation.Gradle
import org.gradle.internal.InternalBuildAdapter
import org.gradle.internal.event.ListenerManager
import org.gradle.plugin.use.internal.DefaultPluginId
import spock.lang.Specification

class DefaultPluginResolutionStrategyTest extends Specification {

    def listenerManager = Mock(ListenerManager)
    InternalBuildAdapter listener
    DefaultPluginResolutionStrategy strategy

    def setup() {
        // The strategy registers an InternalBuildAdapter that locks it when projects are loaded;
        // capture it so the tests can simulate that lifecycle event. Stub before constructing, since
        // the constructor registers the listener.
        listenerManager.addListener(_) >> { InternalBuildAdapter l -> listener = l }
        strategy = new DefaultPluginResolutionStrategy(listenerManager)
    }

    private void lockStrategy() {
        listener.projectsLoaded(Mock(Gradle))
    }

    private PluginRequestInternal request(String id, String version = null) {
        new DefaultPluginRequest(DefaultPluginId.of(id), true, PluginRequestInternal.Origin.OTHER, "test", 1, version, null, null, null, null)
    }

    private PluginRequestInternal aliasRequest(String id, @DelegatesTo(MutableVersionConstraint) Closure<?> version = {}) {
        def constraint = new DefaultMutableVersionConstraint("")
        version.delegate = constraint
        version.resolveStrategy = Closure.DELEGATE_FIRST
        version.call()
        new DefaultPluginRequest(DefaultPluginId.of(id), true, PluginRequestInternal.Origin.OTHER, "test", 1, null, null, null, null, constraint)
    }

    def "applies a default version set before projects are loaded"() {
        when:
        strategy.setDefaultPluginVersion(DefaultPluginId.of("org.example"), "1.0")

        then: "the version is applied to requests without an explicit version"
        strategy.applyTo(request("org.example")).version == "1.0"
    }

    def "throws when a default version is set after projects are loaded"() {
        given:
        lockStrategy()

        when:
        strategy.setDefaultPluginVersion(DefaultPluginId.of("org.example"), "1.0")

        then:
        def e = thrown(IllegalStateException)
        e.message == "Cannot set a default plugin version for plugin 'org.example' after projects have been loaded."

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
    }

    def "useVersion drops the constraint that came from a catalog"() {
        given:
        strategy.eachPlugin { it.useVersion("1.0") }

        when:
        def target = strategy.applyTo(aliasRequest("org.example") {prefer "2.0"})

        then:
        target.version == "1.0"
        target.versionConstraint == null
    }

    def "useModule drops the constraint that came from a catalog"() {
        given:
        strategy.eachPlugin { it.useModule("org.example:plugin:1.0") }

        when:
        def target = strategy.applyTo(aliasRequest("org.example") {prefer "2.0"})

        then:
        target.versionConstraint == null
    }

    def "a default version wins over a preferred version from a catalog"() {
        given:
        strategy.setDefaultPluginVersion(DefaultPluginId.of("org.example"), "1.0")

        when:
        def target = strategy.applyTo(aliasRequest("org.example") {prefer "2.0"})

        then:
        target.version == "1.0"
        target.versionConstraint == null
    }
}
