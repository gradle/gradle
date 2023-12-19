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

package org.gradle.configurationcache.isolated


import org.gradle.integtests.fixtures.configurationcache.ConfigurationCacheProblemsFixture
import org.gradle.integtests.fixtures.configurationcache.HasConfigurationCacheProblemsSpec
import org.gradle.test.fixtures.file.TestFile

import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

class IdeSyncFixture {

    private final ConfigurationCacheProblemsFixture configurationCacheProblemsFixture
    private final TestFile rootDir

    IdeSyncFixture(TestFile rootDir) {
        this.configurationCacheProblemsFixture = new ConfigurationCacheProblemsFixture(rootDir)
        this.rootDir = rootDir
    }

    void assertHtmlReportHasProblems(
        @DelegatesTo(value = HasConfigurationCacheProblemsSpec, strategy = Closure.DELEGATE_FIRST) Closure<?> specClosure
    ) {
        def reportDir = resolveFirstConfigurationCacheReportDir(rootDir)
        configurationCacheProblemsFixture.assertHtmlReportHasProblems(
            reportDir,
            configurationCacheProblemsFixture.newProblemsSpec(specClosure)
        )
    }

    private static TestFile resolveFirstConfigurationCacheReportDir(TestFile rootDir) {
        def reportsDir = rootDir.file("build/reports/configuration-cache")
        TestFile reportDir = null
        Files.walkFileTree(reportsDir.toPath(), new SimpleFileVisitor<Path>() {
            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                if (file.getFileName().toString() == "configuration-cache-report.html") {
                    reportDir = new TestFile(file.parent.toString())
                    return FileVisitResult.TERMINATE;
                }
                return FileVisitResult.CONTINUE;
            }
        })

        if (reportDir == null) {
            throw new RuntimeException()
        }

        return reportDir
    }
}
