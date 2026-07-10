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

package org.gradle.performance.mutator

import org.apache.commons.io.file.PathUtils
import org.gradle.profiler.BuildMutator
import org.gradle.profiler.mutations.AbstractScheduledMutator

import java.nio.file.FileVisitResult
import java.nio.file.FileVisitor
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.attribute.BasicFileAttributes

import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.ANALYSIS_OUTPUT_DIR
import static org.gradle.api.internal.initialization.transform.utils.InstrumentationTransformUtils.MERGE_OUTPUT_DIR
import static org.gradle.internal.classpath.TransformedClassPath.FileMarker.INSTRUMENTATION_CLASSPATH_MARKER

/**
 * A mutator that cleans up the artifact transform cache directory, but leaves folders with the instrumented jars.
 *
 * Since buildscript classpath instrumentation also uses artifact transforms, we can avoid
 * re-instrumenting jars by applying this mutator.
 *
 * In other words, this mutator can be applied to a scenario that tests performance of artifact transforms,
 * but does not want to test impact of re-instrumenting jars.
 *
 * This mutator could be also moved to the gradle-profiler.
 * TODO Refactor gradle-profiler `AbstractCacheCleanupMutator` so that it can clean up files in `caches/<gradle-version>/<cache-dir>`
 */
class ClearArtifactTransformCacheWithoutInstrumentedJarsMutator extends AbstractScheduledMutator {

    /**
     * This marker was used in Gradle 8.7 and earlier.
     */
    static final String GRADLE_PRE_8_8_CLASSPATH_MAKER = ".gradle-instrumented.marker"

    private final File gradleUserHome;

    ClearArtifactTransformCacheWithoutInstrumentedJarsMutator(File gradleUserHome, Schedule schedule) {
        super(schedule);
        this.gradleUserHome = gradleUserHome
    }

    @Override
    protected void executeOnSchedule() {
        System.out.println("> Cleaning '<gradle-home>/caches/transforms-*/' and '<gradle-home>/caches/<gradle-version>/transforms/*' caches in " + gradleUserHome)
        File caches = new File(gradleUserHome, "caches")

        // <gradle-home>/caches/transforms-*
        List<File> oldCaches = caches.listFiles().findAll {
            it.isDirectory() && it.name.startsWith("transforms-")
        }

        // <gradle-home>/caches/<gradle-version>/transforms/*
        List<File> newCaches = caches.listFiles().findAll {
            it.isDirectory() && it.name.matches("\\d.*") && new File(it, "transforms").isDirectory()
        }.collect {
            new File(it, "transforms")
        }

        (oldCaches + newCaches).forEach {
            cleanupCacheDir(it)
        }
    }

    protected void cleanupCacheDir(File cacheDir) {
        Files.walkFileTree(cacheDir.toPath(), new FileVisitor<Path>() {
            @Override
            FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                if (hasInstrumentationClasspathMarkerFile(dir)) {
                    return FileVisitResult.SKIP_SUBTREE
                }
                return FileVisitResult.CONTINUE
            }

            private boolean hasInstrumentationClasspathMarkerFile(Path transformedDir) {
                return Files.exists(transformedDir.resolve("transformed/$GRADLE_PRE_8_8_CLASSPATH_MAKER")) ||
                    Files.exists(transformedDir.resolve("transformed/${INSTRUMENTATION_CLASSPATH_MARKER.fileName}")) ||
                    Files.exists(transformedDir.resolve("transformed/$ANALYSIS_OUTPUT_DIR/${INSTRUMENTATION_CLASSPATH_MARKER.fileName}")) ||
                    Files.exists(transformedDir.resolve("transformed/$MERGE_OUTPUT_DIR/${INSTRUMENTATION_CLASSPATH_MARKER.fileName}"))
            }

            @Override
            FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.delete(file)
                return FileVisitResult.CONTINUE
            }

            @Override
            FileVisitResult visitFileFailed(Path file, IOException exc) throws IOException {
                throw exc
            }

            @Override
            FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                if (PathUtils.isEmpty(dir)) {
                    Files.delete(dir)
                }
                return FileVisitResult.CONTINUE
            }
        })
    }

    static BuildMutator create(File gradleUserHome, Schedule schedule) {
        return new ClearArtifactTransformCacheWithoutInstrumentedJarsMutator(gradleUserHome, schedule)
    }
}
