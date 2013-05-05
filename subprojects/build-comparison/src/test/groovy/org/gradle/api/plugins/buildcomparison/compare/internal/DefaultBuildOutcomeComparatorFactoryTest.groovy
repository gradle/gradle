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

package org.gradle.api.plugins.buildcomparison.compare.internal

import org.gradle.api.plugins.buildcomparison.outcome.internal.BuildOutcome
import org.gradle.api.plugins.buildcomparison.outcome.internal.BuildOutcomeAssociation
import spock.lang.Specification

class DefaultBuildOutcomeComparatorFactoryTest extends Specification {

    def factory = new DefaultBuildOutcomeComparatorFactory()

    private static class BuildOutcome1 implements BuildOutcome {
        String getName() { "1" }
        String getDescription() { "1" }
    }

    private static class BuildOutcome2 implements BuildOutcome {
        String getName() { "2" }
        String getDescription() { "2" }
    }

    def buildOutcome1 = new BuildOutcome1()
    def buildOutcome2 = new BuildOutcome2()

    def comparator1 = toBuildOutcomeComparator(buildOutcome1)
    def comparator2 = toBuildOutcomeComparator(buildOutcome2)

    def "can register comparator"() {
        expect:
        factory.getComparator(buildOutcome1.class) == null

        when:
        factory.registerComparator(comparator1)

        then:
        factory.getComparator(buildOutcome1.class) == comparator1
    }

    def "comparators match registration"() {
        when:
        factory.registerComparator(comparator1)
        factory.registerComparator(comparator2)

        then:
        factory.getComparator(buildOutcome1.class) == comparator1
        factory.getComparator(buildOutcome2.class) == comparator2
    }

    def "registering for existing replaces"() {
        given:
        factory.registerComparator(comparator1)

        expect:
        factory.getComparator(buildOutcome1.class) == comparator1

        when:
        def replacement = toBuildOutcomeComparator(buildOutcome1)
        factory.registerComparator(replacement)

        then:
        factory.getComparator(buildOutcome1.class) == replacement
    }

    private static BuildOutcomeComparator toBuildOutcomeComparator(BuildOutcome buildOutcome) {
        new BuildOutcomeComparator<>() {
            String toString() {
                "comparator for $buildOutcome"
            }

            Class getComparedType() {
                return buildOutcome.class
            }

            BuildOutcomeComparisonResult compare(BuildOutcomeAssociation association) {
                return null
            }
        }
    }
}
