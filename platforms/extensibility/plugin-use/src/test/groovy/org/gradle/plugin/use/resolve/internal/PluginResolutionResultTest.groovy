/*
 * Copyright 2026 Gradle and contributors.
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

package org.gradle.plugin.use.resolve.internal

import com.google.common.collect.ImmutableList
import org.gradle.api.plugins.UnknownPluginException
import org.gradle.internal.exceptions.LocationAwareException
import org.gradle.internal.exceptions.MultiCauseException
import org.gradle.plugin.management.internal.PluginRequestInternal
import org.gradle.plugin.use.PluginId
import spock.lang.Specification

class PluginResolutionResultTest extends Specification {

    def request = Mock(PluginRequestInternal) {
        getDisplayName() >> "[id: 'org.test.foo', version: '1.0']"
        getId() >> Mock(PluginId) { getId() >> "org.test.foo" }
        getScriptDisplayName() >> "build file 'build.gradle'"
        getLineNumber() >> 2
    }

    def "reports the failure of a single source as the cause"() {
        given:
        def failure = new RuntimeException("repository is on fire")

        expect:
        causeOf(sources(notFound("Plugin Repositories", failure))).is(failure)
    }

    def "ignores the sources that reported no failure"() {
        given:
        def failure = new RuntimeException("repository is on fire")
        def result = sources(
            notFound("Gradle Core Plugins"),
            notFound("Included Builds"),
            notFound("Plugin Repositories", failure)
        )

        expect:
        causeOf(result).is(failure)
    }

    def "folds the failures of several sources into one cause"() {
        given:
        def first = new RuntimeException("first repository is on fire")
        def second = new RuntimeException("second repository is on fire")

        when:
        def cause = causeOf(sources(notFound("First", first), notFound("Second", second)))

        then:
        cause instanceof MultiCauseException
        cause.causes == [first, second]
    }

    def "leaves the reported exception without a cause when no source reported a failure"() {
        expect:
        causeOf(sources(notFound("Plugin Repositories"))) == null
    }

    private Throwable causeOf(PluginResolutionResult result) {
        try {
            result.getFound(request)
        } catch (LocationAwareException e) {
            assert e.cause instanceof UnknownPluginException
            return e.cause.cause
        }
        throw new AssertionError("expected getFound to fail" as Object)
    }

    private static PluginResolutionResult sources(List<PluginResolutionResult.NotFound>... notFound) {
        PluginResolutionResult.notFound(ImmutableList.copyOf(notFound.flatten()))
    }

    private static List<PluginResolutionResult.NotFound> notFound(String source, Throwable... failures) {
        PluginResolutionResult.notFound(source, "message", "detail", failures.toList()).getNotFound()
    }
}
