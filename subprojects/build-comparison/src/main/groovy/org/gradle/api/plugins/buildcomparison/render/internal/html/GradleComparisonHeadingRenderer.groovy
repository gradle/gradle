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

class GradleComparisonHeadingRenderer implements PartRenderer {

    final GradleBuildInvocationSpec sourceBuild
    final GradleBuildInvocationSpec targetBuild

    GradleComparisonHeadingRenderer(GradleBuildInvocationSpec sourceBuild, GradleBuildInvocationSpec targetBuild) {
        this.sourceBuild = sourceBuild
        this.targetBuild = targetBuild
    }

    void render(BuildComparisonResult result, HtmlRenderContext context) {
        context.render {
            h1 "Gradle Build Comparison"
            h4 "Builds Compared"
            table {
                tr {
                    th class: "border-right", ""
                    th "Location"
                    th "Gradle Version"
                }
                tr {
                    th class: "border-right no-border-bottom", "Source Build"
                    td sourceBuild.projectDir.absolutePath
                    td sourceBuild.gradleVersion
                }
                tr {
                    th class: "border-right no-border-bottom", "Target Build"
                    td targetBuild.projectDir.absolutePath
                    td targetBuild.gradleVersion
                }
            }
        }
    }
}
