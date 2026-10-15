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

package org.gradle.internal.file

import org.apache.tools.ant.DirectoryScanner
import org.gradle.internal.file.excludes.GradleDefaultExcludes
import spock.lang.Specification

/**
 * Behavioral-parity guard for the Gradle-owned port of Ant's default excludes.
 * If Ant ever changes its DEFAULTEXCLUDES list (e.g. on a version bump),
 * this test fails so we can review the divergence and decide whether to track it.
 */
class GradleDefaultExcludesTest extends Specification {

    def "Gradle default excludes match Ant's DirectoryScanner default excludes verbatim"() {
        given:
        DirectoryScanner.resetDefaultExcludes()

        expect:
        new HashSet<String>(GradleDefaultExcludes.DEFAULT_EXCLUDES) == new HashSet<String>(Arrays.asList(DirectoryScanner.getDefaultExcludes()))
    }

    def "GradleDefaultExcludes list is immutable"() {
        when:
        GradleDefaultExcludes.DEFAULT_EXCLUDES.add("**/foo")

        then:
        thrown(UnsupportedOperationException)
    }
}
