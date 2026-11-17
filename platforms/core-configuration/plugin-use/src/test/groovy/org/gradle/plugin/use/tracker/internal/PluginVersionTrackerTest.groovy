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

package org.gradle.plugin.use.tracker.internal

import org.gradle.api.internal.initialization.ClassLoaderScope
import spock.lang.Specification

class PluginVersionTrackerTest extends Specification {

    def tracker = new PluginVersionTracker()

    // A root scope is its own parent, see RootClassLoaderScope#getParent()
    def root = scope(null)
    def settings = scope(root)
    def rootProject = scope(settings)
    def child = scope(rootProject)

    def "returns null when nothing was recorded for the plugin"() {
        expect:
        tracker.findPluginVersionAt(child, "org.example.plugin") == null
    }

    def "finds a version recorded at the very same scope"() {
        when:
        tracker.setPluginVersionAt(child, "org.example.plugin", "1.0", false)

        then:
        with(tracker.findPluginVersionAt(child, "org.example.plugin")) {
            version == "1.0"
            !local
        }
    }

    def "finds a version recorded at an ancestor scope"() {
        when:
        tracker.setPluginVersionAt(settings, "org.example.plugin", "1.0", false)

        then:
        tracker.findPluginVersionAt(child, "org.example.plugin").version == "1.0"
    }

    def "the closest ancestor wins"() {
        given:
        tracker.setPluginVersionAt(settings, "org.example.plugin", "1.0", false)
        tracker.setPluginVersionAt(rootProject, "org.example.plugin", "2.0", false)

        expect:
        tracker.findPluginVersionAt(child, "org.example.plugin").version == "2.0"
        tracker.findPluginVersionAt(settings, "org.example.plugin").version == "1.0"
    }

    def "walking up terminates at a scope that is its own parent"() {
        expect:
        tracker.findPluginVersionAt(root, "org.example.plugin") == null
    }

    def "a version-less plugin that is not local is not recorded"() {
        when:
        tracker.setPluginVersionAt(child, "org.example.plugin", null, false)

        then:
        tracker.findPluginVersionAt(child, "org.example.plugin") == null
    }

    def "a version-less local plugin is recorded so that it can be recognised as local"() {
        when:
        tracker.setPluginVersionAt(child, "org.example.plugin", null, true)

        then:
        with(tracker.findPluginVersionAt(child, "org.example.plugin")) {
            version == null
            local
        }
    }

    def "the local flag is visible from a descendant scope"() {
        when:
        tracker.setPluginVersionAt(rootProject, "org.example.plugin", null, true)

        then:
        tracker.findPluginVersionAt(child, "org.example.plugin").local
    }

    def "an unrecorded version-less plugin does not shadow a local ancestor entry"() {
        given:
        tracker.setPluginVersionAt(rootProject, "org.example.plugin", null, true)

        when: "a descendant resolves the same plugin without a version and without being local"
        tracker.setPluginVersionAt(child, "org.example.plugin", null, false)

        then: "the ancestor entry is still what a deeper scope sees"
        tracker.findPluginVersionAt(child, "org.example.plugin").local
    }

    def "recording the same plugin twice at the same scope fails"() {
        given:
        tracker.setPluginVersionAt(child, "org.example.plugin", "1.0", false)

        when:
        tracker.setPluginVersionAt(child, "org.example.plugin", "1.0", false)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Plugin version already set for org.example.plugin to version 1.0"
    }

    def "recording the same version-less local plugin twice at the same scope fails"() {
        given:
        tracker.setPluginVersionAt(child, "org.example.plugin", null, true)

        when:
        tracker.setPluginVersionAt(child, "org.example.plugin", null, true)

        then:
        def e = thrown(IllegalStateException)
        e.message == "Plugin version already set for org.example.plugin as local plugin"
    }

    def "the same plugin can be recorded at different scopes"() {
        when:
        tracker.setPluginVersionAt(settings, "org.example.plugin", "1.0", false)
        tracker.setPluginVersionAt(child, "org.example.plugin", "2.0", false)

        then:
        noExceptionThrown()
    }

    def "different plugins can be recorded at the same scope"() {
        when:
        tracker.setPluginVersionAt(child, "org.example.one", "1.0", false)
        tracker.setPluginVersionAt(child, "org.example.two", "2.0", false)

        then:
        tracker.findPluginVersionAt(child, "org.example.one").version == "1.0"
        tracker.findPluginVersionAt(child, "org.example.two").version == "2.0"
    }

    private ClassLoaderScope scope(ClassLoaderScope parent) {
        ClassLoaderScope scope = Stub(ClassLoaderScope)
        scope.getParent() >> { parent == null ? scope : parent }
        return scope
    }
}
