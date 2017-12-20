/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.tooling.r40

import org.gradle.integtests.tooling.fixture.ProgressEvents
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.TextUtil
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.events.OperationType
import org.gradle.util.GradleVersion

import java.util.concurrent.TimeUnit

@ToolingApiVersion('>=3.3')
@TargetGradleVersion(">=4.0")
class BuildCacheCleanupOperationsCrossVersionSpec extends ToolingApiSpecification {
    def cacheDir = file("task-output-cache")
    private boolean sizeBasedCleanup

    def setup() {
        buildFile << """
            @CacheableTask
            class CustomTask extends DefaultTask {
                @OutputFile File outputFile = new File(temporaryDir, "output.txt")
                @Input String run = project.findProperty("run") ?: ""
                @TaskAction 
                void generate() {
                    logger.warn("Run " + run)
                    def data = new byte[1024*1024]
                    new Random().nextBytes(data)
                    outputFile.bytes = data
                }
            }
            
            task cacheable(type: CustomTask) {
                description = "Generates a 1MB file"
            }
        """

        def cacheLimit
        sizeBasedCleanup = targetVersion < GradleVersion.version("4.5")
        if (!sizeBasedCleanup) {
            cacheLimit = "removeUnusedEntriesAfterDays = 1"
        } else {
            cacheLimit = "targetSizeInMB = 2"
        }

        settingsFile << """
            buildCache {
                local(DirectoryBuildCache) {
                    ${cacheLimit}
                    directory = "${TextUtil.escapeString(cacheDir.absolutePath)}"
                }
            }
        """
    }

    def "generates cleanup events"() {
        when:
        (1..4).each { run ->
            withConnection {
                ProjectConnection connection ->
                    connection.newBuild().
                        withArguments("--build-cache", "-Prun=" + run).
                        forTasks("cacheable").
                        run()
            }
        }
        then:
        assert cacheDir.directorySize() >= 4 * 1024 * 1024

        when:
        def gcFile = cacheDir.file("gc.properties")
        def oldTimestamp = gcFile.lastModified() - TimeUnit.DAYS.toMillis(60)
        cacheDir.eachFile { file ->
            file.lastModified = oldTimestamp
        }
        and:
        def listener = ProgressEvents.create()
        withConnection {
            ProjectConnection connection ->
                connection.newBuild().
                    withArguments("--build-cache").
                    forTasks("cacheable").
                    addProgressListener(listener, EnumSet.of(OperationType.GENERIC)).
                    run()
        }
        then:
        listener.operation("Clean up Build cache (" + cacheDir + ")")

        cacheDir.directorySize() <= 2*1024*1024
    }
}
