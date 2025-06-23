/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.reporting.internal

import org.gradle.api.Project
import spock.lang.Specification

class ReportUtilitiesTest extends Specification {
    def project = Mock(Project)

    def "produces expected title when version is provided"() {
        project.name >> "test"
        project.version >> "1.0"
        expect:
        ReportUtilities.getApiDocTitleFor(project) == "test 1.0 API"
    }

    def "produces expected title when version is unspecified"() {
        project.name >> "test"
        project.version >> Project.DEFAULT_VERSION
        expect:
        ReportUtilities.getApiDocTitleFor(project) == "test API"
    }
}
