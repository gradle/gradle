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

package org.gradle.integtests.composite

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.file.TestFile

class CompositeBuildProjectDepthIntegrationTest extends AbstractIntegrationSpec {

    def "project depth is independent from composite builds"() {
        def printDepthExpr = buildScriptSnippet """
            println("Project '" + project.buildTreePath + "' depth = " + project.depth)
        """

        def subprojectsPrintingDepth = { TestFile dir ->
            settingsFile dir.file("settings.gradle"), """
                include(":bus")
                include(":sub")
                include(":sub:sub")
            """
            dir.file("build.gradle") << printDepthExpr
            dir.file("bus/build.gradle") << printDepthExpr
            dir.file("sub/build.gradle") << printDepthExpr
            dir.file("sub/sub/build.gradle") << printDepthExpr
        }

        settingsFile """
            includeBuild("included")
        """
        settingsFile "included/settings.gradle", """
            includeBuild("nested")
        """

        subprojectsPrintingDepth(file("."))
        subprojectsPrintingDepth(file("buildSrc"))
        subprojectsPrintingDepth(file("buildSrc/buildSrc"))
        subprojectsPrintingDepth(file("included"))
        subprojectsPrintingDepth(file("included/nested"))

        when:
        run "help"

        then:
        outputContains("Project ':included:nested' depth = 0")
        outputContains("Project ':included:nested:bus' depth = 1")
        outputContains("Project ':included:nested:sub' depth = 1")
        outputContains("Project ':included:nested:sub:sub' depth = 2")

        outputContains("Project ':included' depth = 0")
        outputContains("Project ':included:bus' depth = 1")
        outputContains("Project ':included:sub' depth = 1")
        outputContains("Project ':included:sub:sub' depth = 2")

        outputContains("Project ':buildSrc:buildSrc' depth = 0")
        outputContains("Project ':buildSrc:buildSrc:bus' depth = 1")
        outputContains("Project ':buildSrc:buildSrc:sub' depth = 1")
        outputContains("Project ':buildSrc:buildSrc:sub:sub' depth = 2")

        outputContains("Project ':buildSrc' depth = 0")
        outputContains("Project ':buildSrc:bus' depth = 1")
        outputContains("Project ':buildSrc:sub' depth = 1")
        outputContains("Project ':buildSrc:sub:sub' depth = 2")

        outputContains("Project ':' depth = 0")
        outputContains("Project ':bus' depth = 1")
        outputContains("Project ':sub' depth = 1")
        outputContains("Project ':sub:sub' depth = 2")
    }
}
