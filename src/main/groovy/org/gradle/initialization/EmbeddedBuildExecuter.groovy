/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.initialization

import org.gradle.Build
import org.gradle.api.internal.project.BuildScriptFinder
import org.gradle.api.internal.project.EmbeddedBuildScriptFinder

/**
 * @author Hans Dockter
 */
class EmbeddedBuildExecuter {
    Closure buildFactory
    File gradleUserHome

    EmbeddedBuildExecuter() {}

    EmbeddedBuildExecuter(Closure buildFactory, File gradleUserHome) {
        this.buildFactory = buildFactory
        this.gradleUserHome = gradleUserHome
    }

    void execute(File buildResolverDir, File projectDir, String buildScriptName, List taskNames, Map projectProperties, Map systemProperties,
                 boolean recursive, boolean searchUpwards) {
        Build build = buildFactory(new BuildScriptFinder(buildScriptName), buildResolverDir)
        build.run(taskNames, projectDir, recursive, searchUpwards, projectProperties, systemProperties)
    }

    void executeEmbeddedScript(File buildResolverDir, File projectDir, String embeddedScript, List taskNames, Map projectProperties, Map systemProperties) {
        Build build = buildFactory(new EmbeddedBuildScriptFinder(embeddedScript),  buildResolverDir)
        build.run(taskNames, projectDir, projectProperties, systemProperties)
    }
}
