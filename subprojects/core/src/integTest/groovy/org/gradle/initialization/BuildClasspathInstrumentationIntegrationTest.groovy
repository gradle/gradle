/*
 * Copyright 2023 the original author or authors.
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


import org.gradle.api.internal.artifacts.ivyservice.CacheLayout
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.cache.FileAccessTimeJournalFixture
import org.gradle.test.fixtures.file.TestFile

import java.nio.file.Files
import java.util.regex.Pattern
import java.util.stream.Collectors

class BuildClasspathInstrumentationIntegrationTest extends AbstractIntegrationSpec implements FileAccessTimeJournalFixture {

    def "buildSrc should be cached in global cache"() {
        given:
        // Run with a clean global cache
        executer.requireOwnGradleUserHomeDir()
        withBuildSrc()

        when:
        run("tasks")

        then:
        gradleUserHomeOutput("buildSrc.jar").exists()
        gradleUserHomeOutput("buildSrc.jiar").exists()
    }

    def "buildSrc should be just instrumented and not upgraded"() {
        given:
        withBuildSrc()

        when:
        run("tasks", "--info")

        then:
        allTransformsFor("buildSrc.jar") == ["InstrumentArtifactTransform"]
    }

    def withBuildSrc() {
        file("buildSrc/src/main/java/Thing.java") << "class Thing { }"
        file("buildSrc/settings.gradle") << "\n"
    }

    List<String> allTransformsFor(String fileName) {
        List<String> transforms = []
        def pattern = Pattern.compile("Transforming " + fileName + ".* with (.*)")
        for (def line : output.readLines()) {
            def matcher = pattern.matcher(line)
            if (matcher.matches()) {
                transforms.add(matcher.group(1))
            }
        }
        return transforms
    }

    TestFile gradleUserHomeOutput(String outputEndsWith, File cacheDir = getCacheDir()) {
        findOutput(outputEndsWith, cacheDir)
    }

    TestFile findOutput(String outputEndsWith, File cacheDir) {
        def dirs = findOutputs(outputEndsWith, cacheDir)
        if (dirs.size() == 1) {
            return dirs.first()
        }
        throw new AssertionError("Could not find exactly one output directory for $outputEndsWith: $dirs")
    }

    Set<TestFile> gradleUserHomeOutputs(String outputEndsWith, File cacheDir = getCacheDir()) {
        findOutputs(outputEndsWith, cacheDir)
    }

    Set<TestFile> findOutputs(String outputEndsWith, File cacheDir) {
        return Files.find(cacheDir.toPath(), 4, (path, attributes) -> path.toString().endsWith(outputEndsWith))
            .map { new TestFile(it.toFile()) }
            .collect(Collectors.toSet())
    }

    TestFile getCacheDir() {
        return getUserHomeCacheDir().file(CacheLayout.TRANSFORMS.getKey())
    }
}
