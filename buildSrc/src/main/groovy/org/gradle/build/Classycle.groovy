/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.build

import org.gradle.api.DefaultTask
import org.gradle.api.internal.project.IsolatedAntBuilder
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.SourceSetOutput
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject

@CacheableTask
class Classycle extends DefaultTask {

    @InputFiles
    SourceSetOutput sourceSetOutput
    @Input
    String reportName
    @Internal
    File analysis

    void setSourceSet(SourceSet sourceSet) {
        this.sourceSetOutput = sourceSet.output
        this.reportName = sourceSet.name
    }

    @OutputFile
    File report

    @Input
    Set<String> excludePatterns

    @Internal
    private ReportingExtension reporting

    void setReporting(ReportingExtension reportingExtension) {
        this.reporting = reportingExtension
        report = reporting.file("classycle/${reportName}.txt")
        analysis = reporting.file("classycle/${reportName}_analysis.xml")
    }

    @Inject
    public IsolatedAntBuilder getAntBuilder() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    void generate() {
        if (!sourceSetOutput.classesDir.directory) {
            return;
        }
        antBuilder.withClasspath(project.configurations.classycle.files).execute {
            ant.taskdef(name: "classycleDependencyCheck", classname: "classycle.ant.DependencyCheckingTask")
            ant.taskdef(name: "classycleReport", classname: "classycle.ant.ReportTask")
            report.parentFile.mkdirs()
            try {
                ant.classycleDependencyCheck(reportFile: report, failOnUnwantedDependencies: true, mergeInnerClasses: true,
                    """
                        show allResults
                        check absenceOfPackageCycles > 1 in org.gradle.*
                    """
                ) {
                    fileset(dir: sourceSetOutput.classesDir) {
                        excludePatterns.each { excludePattern ->
                            exclude(name: excludePattern)
                        }
                    }
                }
            } catch (e) {
                try {
                    ant.unzip(src: project.rootProject.file("gradle/classycle_report_resources.zip"), dest: reporting.file("classcycle"))
                    ant.classycleReport(reportFile: analysis, reportType: 'xml', mergeInnerClasses: true, title: "${project.name} ${reportName} (${path})") {
                        fileset(dir: sourceSetOutput.classesDir) {
                            excludePatterns.each { excludePattern ->
                                exclude(name: excludePattern)
                            }
                        }
                    }
                } catch (e2) {
                    e2.printStackTrace()
                }
                def clickableUrl = {
                    new URI("file", "", it.toURI().getPath(), null, null).toString()
                }
                throw new RuntimeException("Classycle check failed: $e.message. See failure report at ${clickableUrl(report)} and analysis report at ${clickableUrl(analysis)}", e)
            }
        }
    }
}
