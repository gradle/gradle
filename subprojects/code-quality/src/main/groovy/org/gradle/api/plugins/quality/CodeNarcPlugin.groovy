/*
 * Copyright 2011 the original author or authors.
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

import groovy.transform.CompileStatic
import org.gradle.api.artifacts.DependencySet
import org.gradle.api.internal.plugins.DslObject
import org.gradle.api.plugins.GroovyBasePlugin
import org.gradle.api.plugins.quality.internal.AbstractCodeQualityPlugin
import org.gradle.api.reporting.Report
import org.gradle.api.tasks.GroovySourceSet
import org.gradle.api.tasks.SourceSet

@CompileStatic
class CodeNarcPlugin extends AbstractCodeQualityPlugin<CodeNarc> {
    public static final String DEFAULT_CODENARC_VERSION = "0.24.1"
    private CodeNarcExtension extension

    @Override
    protected String getToolName() {
        return "CodeNarc"
    }

    @Override
    protected Class<CodeNarc> getTaskType() {
        return CodeNarc
    }

    @Override
    protected Class<?> getBasePlugin() {
        return GroovyBasePlugin
    }

    @Override
    protected CodeQualityExtension createExtension() {
        extension = project.extensions.create("codenarc", CodeNarcExtension, project)
        extension.with {
            toolVersion = DEFAULT_CODENARC_VERSION
            config = project.resources.text.fromFile(project.rootProject.file("config/codenarc/codenarc.xml"))
            maxPriority1Violations = 0
            maxPriority2Violations = 0
            maxPriority3Violations = 0
            reportFormat = "html"
        }
        return extension
    }

    @Override
    protected void configureTaskDefaults(CodeNarc task, String baseName) {
        def codenarcConfiguration = project.configurations['codenarc']
        codenarcConfiguration.defaultDependencies { DependencySet dependencies ->
            dependencies.add(project.dependencies.create("org.codenarc:CodeNarc:${extension.toolVersion}"))
        }
        task.conventionMapping.with {
            map('codenarcClasspath') { codenarcConfiguration }
            map('config') { extension.config }
            map('maxPriority1Violations') { extension.maxPriority1Violations }
            map('maxPriority2Violations') { extension.maxPriority2Violations }
            map('maxPriority3Violations') { extension.maxPriority3Violations }
            map('ignoreFailures') { extension.ignoreFailures }
        }

        task.reports.all { Report report ->
            conventionMappingOf(report).with {
                map('enabled') { report.name == extension.reportFormat }
                map('destination') {
                    def fileSuffix = report.name == 'text' ? 'txt' : report.name
                    new File(extension.reportsDir, "$baseName.$fileSuffix")
                }
            }
        }
    }

    @Override
    protected void configureForSourceSet(SourceSet sourceSet, CodeNarc task) {
        task.description = "Run CodeNarc analysis for $sourceSet.name classes"
        def groovySourceSet = new DslObject(sourceSet).convention.getPlugin(GroovySourceSet.class)
        task.setSource(groovySourceSet.allGroovy)
    }
}
