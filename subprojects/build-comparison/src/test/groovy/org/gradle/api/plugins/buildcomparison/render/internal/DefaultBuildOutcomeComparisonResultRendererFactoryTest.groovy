/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins.buildcomparison.render.internal

import spock.lang.Specification
import org.gradle.api.plugins.buildcomparison.outcome.string.StringBuildOutcomeComparisonResultMapRenderer
import org.gradle.api.plugins.buildcomparison.outcome.string.StringBuildOutcomeComparisonResult
import org.gradle.api.plugins.buildcomparison.outcome.internal.archive.GeneratedArchiveBuildOutcomeComparisonResult
import org.gradle.api.plugins.buildcomparison.compare.internal.BuildOutcomeComparisonResult

class DefaultBuildOutcomeComparisonResultRendererFactoryTest extends Specification {

    def contextType = Map
    def factory = createFactory()

    DefaultBuildOutcomeComparisonResultRendererFactory createFactory(contextType = this.contextType) {
        new DefaultBuildOutcomeComparisonResultRendererFactory(contextType)
    }

    def "can register and retrieve"() {
        when:
        def renderer = new StringBuildOutcomeComparisonResultMapRenderer()
        factory.registerRenderer(renderer)

        then:
        factory.getRenderer(StringBuildOutcomeComparisonResult) == renderer
        factory.getRenderer(GeneratedArchiveBuildOutcomeComparisonResult) == null
    }

    def "registration requires the same context type"() {
        given:
        def renderer = new BuildOutcomeComparisonResultRenderer() {
            Class getResultType() { StringBuildOutcomeComparisonResult }
            Class getContextType() { List }
            void render(BuildOutcomeComparisonResult result, Object context) {}
        }

        when:
        factory.registerRenderer(renderer)

        then:
        thrown IllegalArgumentException
    }
}
