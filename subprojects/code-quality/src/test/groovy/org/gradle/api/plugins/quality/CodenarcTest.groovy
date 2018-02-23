/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.plugins.quality

import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification

class CodenarcTest extends Specification {
    def project = ProjectBuilder.builder().build()
    def codenarc = project.tasks.create("codenarc", CodeNarc)

    def "can use legacy configFile property"() {
        codenarc.configFile = project.file("config/file.txt")

        expect:
        codenarc.configFile == project.file("config/file.txt")
        codenarc.config.inputFiles.singleFile == project.file("config/file.txt")
    }

    def "has html/xml/text/console"() {
        expect:
        codenarc.reports.names == ['console', 'html', 'text', 'xml'] as Set
    }
}
