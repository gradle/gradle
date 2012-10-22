/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.plugins

import org.gradle.api.Project
import org.gradle.testfixtures.ProjectBuilder
import spock.lang.Specification
import org.gradle.api.reporting.ReportingExtension

// Note: ReportingBasePluginConvention has been deprecated
public class ReportingBasePluginConventionTest extends Specification {

    Project project = ProjectBuilder.builder().build()
    ReportingExtension extension = new ReportingExtension(project)
    ReportingBasePluginConvention convention = new ReportingBasePluginConvention(project, extension)

    def "defaults to reports dir in build dir"() {
        expect:
        convention.reportsDirName == ReportingExtension.DEFAULT_REPORTS_DIR_NAME
        convention.reportsDir == new File(project.buildDir, ReportingExtension.DEFAULT_REPORTS_DIR_NAME)
    }

    def "can set reports dir by name, relative to build dir"() {
        when:
        convention.reportsDirName = "something-else"
        
        then:
        convention.reportsDir == new File(project.buildDir, "something-else")
        
        when:
        project.buildDir = new File(project.buildDir, "new-build-dir")

        then:
        convention.reportsDir == new File(project.buildDir, "something-else")
    }

    def "calculates api doc title from project name and version"() {
        expect:
        project.version == Project.DEFAULT_VERSION

        and:
        convention.apiDocTitle == "$project.name API"

        when:
        project.version = "1.0"

        then:
        convention.apiDocTitle == "$project.name 1.0 API"
    }

}
