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

package org.gradle.api.internal.initialization

import org.gradle.api.GradleException
import org.gradle.api.file.ProjectLayout
import org.gradle.features.internal.binding.ProjectFeatureDeclarations
import spock.lang.Specification

class DefaultSharedModelDefaultsTest extends Specification {

    def featureDeclarations = Mock(ProjectFeatureDeclarations)
    def defaults = new DefaultSharedModelDefaults(featureDeclarations)

    def "getLayout() throws when no layout is bound"() {
        when:
        defaults.getLayout()

        then:
        def e = thrown(GradleException)
        e.message == "ProjectLayout should be referenced only inside of project type default configuration blocks"
    }

    def "withProjectLayout binds the layout for the duration of the action"() {
        given:
        def layout = Mock(ProjectLayout)
        ProjectLayout observed = null

        when:
        defaults.withProjectLayout(layout) {
            observed = defaults.getLayout()
        }

        then:
        observed.is(layout)

        when:
        defaults.getLayout()

        then:
        thrown(GradleException)
    }

    def "nested withProjectLayout preserves the outer binding after the inner action returns"() {
        given:
        def outer = Mock(ProjectLayout)
        def inner = Mock(ProjectLayout)
        ProjectLayout duringInner = null
        ProjectLayout afterInner = null

        when:
        defaults.withProjectLayout(outer) {
            defaults.withProjectLayout(inner) {
                duringInner = defaults.getLayout()
            }
            afterInner = defaults.getLayout()
        }

        then:
        duringInner.is(inner)
        afterInner.is(outer)

        when:
        defaults.getLayout()

        then:
        thrown(GradleException)
    }

    def "exception inside a nested action restores the outer binding"() {
        given:
        def outer = Mock(ProjectLayout)
        def inner = Mock(ProjectLayout)
        ProjectLayout afterCatch = null

        when:
        defaults.withProjectLayout(outer) {
            try {
                defaults.withProjectLayout(inner) {
                    throw new RuntimeException("boom")
                }
            } catch (RuntimeException ignored) {
            }
            afterCatch = defaults.getLayout()
        }

        then:
        afterCatch.is(outer)
    }

    def "exception with no prior binding clears the ThreadLocal"() {
        given:
        def layout = Mock(ProjectLayout)

        when:
        defaults.withProjectLayout(layout) {
            throw new RuntimeException("boom")
        }

        then:
        thrown(RuntimeException)

        when:
        defaults.getLayout()

        then:
        thrown(GradleException)
    }
}
