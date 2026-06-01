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

import org.gradle.internal.file.excludes.GradleDefaultExcludes
import spock.lang.Specification

class GradleDefaultExcludesTest extends Specification {

    def "GradleDefaultExcludes list is immutable"() {
        when:
        GradleDefaultExcludes.DEFAULT_EXCLUDES.add("**/foo")

        then:
        thrown(UnsupportedOperationException)
    }

    def "GradleDefaultExcludes set is immutable"() {
        when:
        GradleDefaultExcludes.DEFAULT_EXCLUDES_SET.add("**/foo")

        then:
        thrown(UnsupportedOperationException)
    }

    def "DEFAULT_EXCLUDES and DEFAULT_EXCLUDES_SET contain the same patterns"() {
        expect:
        new HashSet<>(GradleDefaultExcludes.DEFAULT_EXCLUDES) == GradleDefaultExcludes.DEFAULT_EXCLUDES_SET
    }
}
