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

package org.gradle.plugins.classycle

import org.gradle.api.DefaultTask
import org.gradle.api.internal.project.IsolatedAntBuilder
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SkipWhenEmpty
import org.gradle.api.tasks.TaskAction

import javax.inject.Inject

@CacheableTask
class Classycle extends DefaultTask {

    @InputDirectory
    @SkipWhenEmpty
    @PathSensitive(PathSensitivity.RELATIVE)
    File classesDir

    @Input
    String reportName

    @OutputFile
    File getReportFile() {
        new File(reportDir, "${reportName}.txt")
    }

    @Internal
    File getAnalysisFile() {
        new File(reportDir, "${reportName}_analysis.xml")
    }

    @Internal
    File reportDir

    @Input
    Set<String> excludePatterns

    @Inject
    public IsolatedAntBuilder getAntBuilder() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    void generate() {
        antBuilder.withClasspath(project.configurations.getByName(ClassyclePlugin.CLASSYCLE_CONFIGURATION_NAME).files).execute {
            ant.taskdef(name: "classycleDependencyCheck", classname: "classycle.ant.DependencyCheckingTask")
            ant.taskdef(name: "classycleReport", classname: "classycle.ant.ReportTask")
            reportFile.parentFile.mkdirs()
            try {
                ant.classycleDependencyCheck(reportFile: reportFile, failOnUnwantedDependencies: true, mergeInnerClasses: true,
                    """
                        show allResults
                        check absenceOfPackageCycles > 1 in org.gradle.*
                    """
                ) {
                    fileset(dir: classesDir) {
                        excludePatterns.each { excludePattern ->
                            exclude(name: excludePattern)
                        }
                    }
                }
            } catch (e) {
                try {
                    ant.unzip(src: project.rootProject.file("gradle/classycle_report_resources.zip"), dest: reportDir)
                    ant.classycleReport(reportFile: analysisFile, reportType: 'xml', mergeInnerClasses: true, title: "${project.name} ${reportName} (${path})") {
                        fileset(dir: classesDir) {
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
                throw new RuntimeException("Classycle check failed: $e.message. See failure report at ${clickableUrl(reportFile)} and analysis report at ${clickableUrl(analysisFile)}", e)
            }
        }
    }
}
