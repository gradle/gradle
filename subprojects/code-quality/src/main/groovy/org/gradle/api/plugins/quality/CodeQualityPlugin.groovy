/*
 * Copyright 2010 the original author or authors.
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

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ReportingBasePlugin
import org.gradle.util.DeprecationLogger

/**
 * A plugin which measures and enforces code quality for Java and Groovy projects.
 *
 * @deprecated use {@link CheckstylePlugin} and {@link CodeNarcPlugin} instead
 */
@Deprecated
public class CodeQualityPlugin implements Plugin<Project> {
    private Project project

    static final String CHECKSTYLE_MAIN_TASK = "checkstyleMain"
    static final String CHECKSTYLE_TEST_TASK = "checkstyleTest"
    static final String CODE_NARC_MAIN_TASK = "codenarcMain"
    static final String CODE_NARC_TEST_TASK = "codenarcTest"

    void apply(Project project) {
        DeprecationLogger.nagUserOfReplacedPlugin('code-quality', 'checkstyle or codenarc')

        this.project = project

        project.plugins.apply(ReportingBasePlugin)
        configureCheckstyle()
        configureCodeNarc()
    }

    private void configureCheckstyle() {
        def javaPluginConvention = new JavaCodeQualityPluginConvention(project)
        project.convention.plugins.javaCodeQuality = javaPluginConvention

        project.plugins.apply(CheckstylePlugin)
        project.checkstyle.conventionMapping.with {
            configFile = { javaPluginConvention.checkstyleConfigFile }
            configProperties = { javaPluginConvention.checkstyleProperties }
            reportsDir = { javaPluginConvention.checkstyleResultsDir }
        }
    }

    private void configureCodeNarc() {
        def groovyPluginConvention = new GroovyCodeQualityPluginConvention(project)
        project.convention.plugins.groovyCodeQuality = groovyPluginConvention

        project.plugins.apply(CodeNarcPlugin)
        project.codenarc.conventionMapping.with {
            configFile = { groovyPluginConvention.codeNarcConfigFile }
            reportFormat = { groovyPluginConvention.codeNarcReportsFormat }
            reportsDir = { groovyPluginConvention.codeNarcReportsDir }
        }
    }
}
