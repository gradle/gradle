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

package org.gradle.api.plugins.buildcomparison.render.internal.html

import org.gradle.api.plugins.buildcomparison.compare.internal.BuildComparisonResult

import org.gradle.api.plugins.buildcomparison.gradle.GradleBuildInvocationSpec
import org.gradle.api.plugins.buildcomparison.gradle.CompareGradleBuilds
import org.gradle.util.GradleVersion
import org.apache.commons.lang.StringEscapeUtils

class GradleComparisonHeadingRenderer implements PartRenderer {

    final CompareGradleBuilds task
    final GradleBuildInvocationSpec targetBuild

    GradleComparisonHeadingRenderer(CompareGradleBuilds task) {
        this.task = task
    }

    void render(BuildComparisonResult result, HtmlRenderContext context) {
        context.render {
            h1 "Gradle Build Comparison"

            p(id: "overall-result", "class": context.equalOrDiffClass(result.buildsAreIdentical)) {
                b result.buildsAreIdentical ?
                    "The build outcomes were found to be identical." :
                    "The build outcomes were not found to be identical."
            }

            div(id: "host-details") {
                h2 "Comparison host details"

                def bits = [
                        "Project": task.project.rootDir.absolutePath,
                        "Task": task.path,
                        "Gradle version": GradleVersion.current().version,
                        "Run at": new Date().toLocaleString()
                ]

                p {
                    mkp.yieldUnescaped bits.collect { "<strong>${it.key}</strong>: ${StringEscapeUtils.escapeHtml(it.value) }" }.join("<br />")
                }
            }

            def sourceBuild = task.sourceBuild
            def targetBuild = task.targetBuild

            div(id: "compared-builds") {
                h2 "Compared builds"
                table {
                    tr {
                        th class: "border-right", ""
                        th "Source Build"
                        th "Target Build"
                    }

                    def sourceBuildPath = sourceBuild.projectDir.absolutePath
                    def targetBuildPath = targetBuild.projectDir.absolutePath
                    tr("class": context.diffClass(sourceBuildPath == targetBuildPath)) {
                        th class: "border-right no-border-bottom", "Project"
                        td sourceBuildPath
                        td targetBuildPath
                    }

                    def sourceGradleVersion = sourceBuild.gradleVersion
                    def targetGradleVersion = targetBuild.gradleVersion
                    tr("class": context.diffClass(sourceGradleVersion == targetGradleVersion)) {
                        th class: "border-right no-border-bottom", "Gradle version"
                        td sourceGradleVersion
                        td targetGradleVersion
                    }

                    def sourceBuildTasks = sourceBuild.tasks
                    def targetBuildTasks = targetBuild.tasks
                    tr("class": context.diffClass(sourceBuildTasks == targetBuildTasks)) {
                        th class: "border-right no-border-bottom", "Tasks"
                        td sourceBuildTasks.join(" ")
                        td targetBuildTasks.join(" ")
                    }

                    def sourceBuildArguments = sourceBuild.arguments
                    def targetBuildArguments = targetBuild.arguments
                    tr("class": context.diffClass(sourceBuildArguments == targetBuildArguments)) {
                        th class: "border-right no-border-bottom", "Arguments"
                        td sourceBuildArguments.join(" ")
                        td targetBuildArguments.join(" ")
                    }
                }
            }
        }
    }

}
