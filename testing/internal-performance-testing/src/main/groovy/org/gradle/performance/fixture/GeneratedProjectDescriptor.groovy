/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.performance.fixture

import groovy.json.JsonSlurper
import groovy.transform.CompileStatic
import org.gradle.test.fixtures.language.Language
import org.jspecify.annotations.Nullable

/**
 * The settings a generated performance test project reports about itself.
 *
 * <p>Written as {@code perf-project.json} by build-builder's {@code perf-project} command. Test
 * fixtures used to read these off the {@code JavaTestProjectGenerator} enum, which meant the
 * generator had to live in this repository; reading the generated project instead lets the
 * generator live in gradle/build-builder.
 *
 * <p>Reading what was generated is also more accurate than consulting an enum: if a project on disk
 * was produced by a different build-builder version, the fixtures see that project's real settings.
 */
@CompileStatic
class GeneratedProjectDescriptor {
    static final String FILE_NAME = "perf-project.json"

    private final Map<String, Object> values

    private GeneratedProjectDescriptor(Map<String, Object> values) {
        this.values = values
    }

    /**
     * Reads the descriptor for the named test project, or returns {@code null} when the project was
     * not produced by build-builder's generator — a project cloned by {@code RemoteProject}, or one
     * expanded from a template, has no descriptor.
     */
    @Nullable
    static GeneratedProjectDescriptor findFor(String testProject) {
        File projectDir
        try {
            projectDir = TestProjectLocator.findProjectDir(testProject)
        } catch (IllegalArgumentException ignored) {
            return null
        }
        File descriptor = new File(projectDir, FILE_NAME)
        if (!descriptor.file) {
            return null
        }
        return new GeneratedProjectDescriptor((Map<String, Object>) new JsonSlurper().parse(descriptor))
    }

    String getProjectName() {
        return values.projectName as String
    }

    Language getLanguage() {
        return Language.valueOf(values.language as String)
    }

    String getDaemonMemory() {
        return values.daemonMemory as String
    }

    boolean getParallel() {
        return values.parallel as boolean
    }

    int getMaxWorkers() {
        return values.maxWorkers as int
    }

    /**
     * The production source file that the named scenario mutates, relative to the project directory.
     */
    String fileToChangeFor(String scenario) {
        Map<String, String> byScenario = (Map<String, String>) values.fileToChangeByScenario
        String file = byScenario?.get(scenario)
        if (file == null) {
            throw new IllegalArgumentException(
                "Generated project '${projectName}' declares no file to change for scenario '${scenario}'. " +
                    "Declared scenarios: ${byScenario?.keySet()?.sort()}")
        }
        return file
    }
}
