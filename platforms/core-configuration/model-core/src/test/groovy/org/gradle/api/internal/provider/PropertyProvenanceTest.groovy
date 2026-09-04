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

package org.gradle.api.internal.provider

import org.gradle.api.internal.provider.provenance.PropertyProvenanceKind
import org.gradle.api.internal.provider.provenance.PropertyProvenanceRecord
import org.gradle.internal.state.ModelObject
import org.gradle.util.internal.TextUtil
import spock.lang.Specification

class PropertyProvenanceTest extends Specification {
    def host = new TrackingHost()

    def "failure trace selects the explicit source and separates the shadowed convention"() {
        def property = new DefaultProperty<String>(host, String)
        host.bindingOrigin = "plugin 'com.example.base'"
        host.bindingLocation = "BasePlugin.kt:12"
        property.convention("default")
        host.bindingOrigin = "build file 'build.gradle.kts'"
        host.bindingLocation = "build.gradle.kts:8"
        property.set(Providers.notDefined())
        host.failureOrigin = "task ':show' action"
        host.failureLocation = "Show.kt:24"

        when:
        property.get()

        then:
        def failure = thrown(MissingValueException)
        failure.message == TextUtil.toPlatformLineSeparators("""Cannot query the value of this property because it has no value available.
Failure trace to source:
    at task ':show' action (Show.kt:24) [get()]
    at build file 'build.gradle.kts' (build.gradle.kts:8) [explicit source]

Shadowed configuration:
    at plugin 'com.example.base' (BasePlugin.kt:12) [convention]""")
    }

    def "failure trace selects the convention when there is no explicit source"() {
        def property = new DefaultProperty<String>(host, String)
        host.bindingOrigin = "plugin 'com.example.base'"
        property.convention(Providers.notDefined())
        host.failureOrigin = "build file 'build.gradle.kts'"
        host.failureLocation = "build.gradle.kts:14"

        when:
        property.get()

        then:
        def failure = thrown(MissingValueException)
        failure.message == TextUtil.toPlatformLineSeparators("""Cannot query the value of this property because it has no value available.
Failure trace to source:
    at build file 'build.gradle.kts' (build.gradle.kts:14) [get()]
    at plugin 'com.example.base' [convention]""")
    }

    def "a replacing set reports only the selected explicit source"() {
        def upstream = new DefaultProperty<String>(host, String)
        host.bindingOrigin = "plugin 'com.example.upstream'"
        upstream.set(Providers.notDefined())

        def property = new DefaultProperty<String>(host, String)
        host.bindingOrigin = "plugin 'com.example.first'"
        property.set(upstream)
        host.bindingOrigin = "plugin 'com.example.second'"
        property.set(Providers.notDefined())
        host.failureOrigin = "task ':show' action"

        when:
        property.get()

        then:
        def failure = thrown(MissingValueException)
        failure.message.contains("at plugin 'com.example.second' [explicit source]")
        !failure.message.contains("com.example.first")
        !failure.message.contains("com.example.upstream")
    }

    def "unsetting an explicit source selects the retained convention"() {
        def property = new DefaultProperty<String>(host, String)
        host.bindingOrigin = "plugin 'com.example.base'"
        property.convention(Providers.notDefined())
        host.bindingOrigin = "build file 'build.gradle.kts'"
        property.set("configured")
        property.unset()
        host.failureOrigin = "task ':show' action"

        when:
        property.get()

        then:
        def failure = thrown(MissingValueException)
        failure.message.contains("at plugin 'com.example.base' [convention]")
        !failure.message.contains("build file 'build.gradle.kts' [explicit source]")
        !failure.message.contains("Shadowed configuration")
    }

    def "finalized set has an ephemeral failure frame and retains the successful source"() {
        def property = new DefaultProperty<String>(host, String)
        host.bindingOrigin = "plugin 'com.example.feature'"
        host.bindingLocation = "FeaturePlugin.kt:10"
        property.set("first")
        property.finalizeValue()
        host.failureOrigin = "build file 'build.gradle.kts'"
        host.failureLocation = "build.gradle.kts:20"

        when:
        property.set("second")

        then:
        def firstFailure = thrown(IllegalStateException)
        firstFailure.message == TextUtil.toPlatformLineSeparators("""The value for this property is final and cannot be changed any further.
Failure trace to source:
    at build file 'build.gradle.kts' (build.gradle.kts:20) [set()]
    at plugin 'com.example.feature' (FeaturePlugin.kt:10) [explicit source]""")

        when:
        host.failureLocation = "build.gradle.kts:21"
        property.set("third")

        then:
        def secondFailure = thrown(IllegalStateException)
        secondFailure.message.contains("(build.gradle.kts:21) [set()]")
        !secondFailure.message.contains("build.gradle.kts:20")
        secondFailure.message.contains("(FeaturePlugin.kt:10) [explicit source]")
    }

    def "indirect mapped Provider evaluation keeps the property trace"() {
        def property = new DefaultProperty<String>(host, String)
        host.bindingOrigin = "plugin 'com.example.feature'"
        property.set(Providers.notDefined())
        host.failureOrigin = "task ':show' action"
        host.failureLocation = "Show.kt:31"

        when:
        property.map { it.length() }.get()

        then:
        def failure = thrown(MissingValueException)
        failure.message.contains("at task ':show' action (Show.kt:31) [get()]")
        failure.message.contains("at plugin 'com.example.feature' [explicit source]")
    }

    def "failure trace follows selected sources across an upstream property chain"() {
        def evaluations = 0
        def source = new DefaultProperty<String>(host, String)
        host.bindingOrigin = "plugin 'com.example.defaults'"
        host.bindingLocation = "DefaultsPlugin.java:10"
        source.convention(new DefaultProvider<String>({
            evaluations++
            null
        }))

        def normalized = new DefaultProperty<String>(host, String)
        host.bindingOrigin = "plugin 'com.example.normalizer'"
        host.bindingLocation = "NormalizerPlugin.java:14"
        normalized.set(source.map { it.trim() })

        def report = new DefaultProperty<String>(host, String)
        host.bindingOrigin = "plugin 'com.example.consumer'"
        host.bindingLocation = "ConsumerPlugin.java:16"
        report.convention("fallback")
        host.bindingOrigin = "build file 'build.gradle.kts'"
        host.bindingLocation = "build.gradle.kts:8"
        report.set(normalized.map { "value=$it" })

        host.failureOrigin = "task ':shareProvenance' action"
        host.failureLocation = "ConsumerPlugin.java:19"

        when:
        report.get()

        then:
        def failure = thrown(MissingValueException)
        failure.message == TextUtil.toPlatformLineSeparators("""Cannot query the value of this property because it has no value available.
Failure trace to source:
    at task ':shareProvenance' action (ConsumerPlugin.java:19) [get()]
    at build file 'build.gradle.kts' (build.gradle.kts:8) [explicit source]
    at plugin 'com.example.normalizer' (NormalizerPlugin.java:14) [explicit source]
    at plugin 'com.example.defaults' (DefaultsPlugin.java:10) [convention]

Shadowed configuration:
    at plugin 'com.example.consumer' (ConsumerPlugin.java:16) [convention]""")
        evaluations == 1
    }

    def "disabled provenance preserves existing messages byte for byte"() {
        def property = new DefaultProperty<String>(new TrackingHost(enabled: false), String)
        property.set(Providers.notDefined())

        when:
        property.get()

        then:
        def missing = thrown(MissingValueException)
        missing.message == "Cannot query the value of this property because it has no value available."
    }

    def "disabled provenance preserves an existing finalized-set message byte for byte"() {
        def property = new DefaultProperty<String>(new TrackingHost(enabled: false), String)
        property.set("first")
        property.finalizeValue()

        when:
        property.set("second")

        then:
        def failure = thrown(IllegalStateException)
        failure.message == "The value for this property is final and cannot be changed any further."
    }

    private static class TrackingHost implements PropertyHost {
        boolean enabled = true
        String bindingOrigin = "unknown code"
        String bindingLocation
        String failureOrigin = "unknown code"
        String failureLocation

        @Override
        String beforeRead(ModelObject producer) {
            return null
        }

        @Override
        boolean tracksPropertyProvenance() {
            return enabled
        }

        @Override
        PropertyProvenanceRecord currentPropertyBinding(PropertyProvenanceKind kind) {
            return new PropertyProvenanceRecord(bindingOrigin, kind, bindingLocation)
        }

        @Override
        PropertyProvenanceRecord currentPropertyFailure(PropertyProvenanceKind kind) {
            return new PropertyProvenanceRecord(failureOrigin, kind, failureLocation)
        }
    }
}
