/*
 * Copyright 2018 the original author or authors.
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

import com.google.common.base.Strings
import groovy.transform.CompileStatic
import groovy.transform.PackageScope

/**
 * Generates flame graphs based on JFR recordings.
 */
@CompileStatic
@PackageScope
class JfrDifferentialFlameGraphGenerator implements ProfilerFlameGraphGenerator {

    private FlameGraphGenerator flameGraphGenerator = new FlameGraphGenerator()
    private final File flamesBaseDirectory

    JfrDifferentialFlameGraphGenerator(File flamesBaseDirectory) {
        this.flamesBaseDirectory = flamesBaseDirectory
    }

    @Override
    File outputDirFor(String testId, BuildExperimentSpec spec) {
        boolean multiVersion = spec instanceof GradleBuildExperimentSpec && spec.multiVersion
        def outputDir
        if (multiVersion) {
            String version = ((GradleBuildExperimentSpec) spec).getInvocation().gradleDistribution.version.version
            outputDir = new File(baseOutputDirFor(testId), version)
        } else {
            outputDir = new File(baseOutputDirFor(testId), fileSafeNameFor(spec.getDisplayName()))
        }
        outputDir.mkdirs()
        return outputDir
    }

    private File baseOutputDirFor(String testId) {
        String fileSafeName = fileSafeNameFor(testId)
        // When the path is too long on Windows, then JProfiler can't write to the JPS file
        // Length 40 seems to work.
        // It may be better to create the flame graph in the tmp directory, and then move it to the right place after the build.
        return new File(flamesBaseDirectory, shortenPath(fileSafeName, 40))
    }

    private static String fileSafeNameFor(String title) {
        def fileSafeName = title.replaceAll('[^a-zA-Z0-9.-]', '-').replaceAll('-+', '-')
        if (fileSafeName.endsWith('-')) {
            fileSafeName = fileSafeName.substring(0, fileSafeName.length() - 1)
        }
        fileSafeName
    }

    private static String shortenPath(String longName, int expectedMaxLength) {
        if (longName.length() <= expectedMaxLength) {
            return longName
        } else {
            return longName.substring(0, expectedMaxLength - 10) + "." + longName.substring(longName.length() - 9)
        }
    }

    @Override
    void generateDifferentialGraphs(String testId) {
        def baseOutputDir = new File(flamesBaseDirectory, shortenPath(fileSafeNameFor(testId), 40))
        Collection<File> experiments = baseOutputDir.listFiles().findAll { it.directory }
        experiments.each { File experiment ->
            experiments.findAll { it != experiment }.each { File baseline ->
                EventType.values().each { EventType type ->
                    // Only create diffs for simplified stacks, diffs for raw stacks don't make much sense
                    DetailLevel level = DetailLevel.SIMPLIFIED
                    def backwardDiff = generateDiff(experiment, baseline, type, level, false)
                    if (backwardDiff) {
                        generateDifferentialFlameGraph(backwardDiff, type, level, false)
                        generateDifferentialIcicleGraph(backwardDiff, type, level, false)
                    }
                    def forwardDiff = generateDiff(experiment, baseline, type, level, true)
                    if (forwardDiff) {
                        generateDifferentialFlameGraph(forwardDiff, type, level, true)
                        generateDifferentialIcicleGraph(forwardDiff, type, level, true)
                    }
                }
            }
        }
    }

    private File generateDiff(File versionUnderTest, File baseline, EventType type, DetailLevel level, boolean negate) {
        File underTestStacks = stacksFileName(versionUnderTest, type, level)
        File baselineStacks = stacksFileName(baseline, type, level)
        if (underTestStacks && baselineStacks) {
            String underTestBasename = stacksBasename(underTestStacks, type, level)
            String baselineTestBasename = stacksBasename(baselineStacks, type, level)
            String commonPrefix = Strings.commonPrefix(underTestBasename, baselineTestBasename)
            String commonSuffix = Strings.commonSuffix(underTestBasename, baselineTestBasename)
            String diffBaseName = "${underTestBasename}-vs-${baselineTestBasename.substring(0, baselineTestBasename.length() - commonSuffix.length()).substring(commonPrefix.length())}${postFixFor(type, level)}-${negate ? "forward-" : "backward-"}diff"
            File diff = new File(underTestStacks.parentFile, "diffs/${diffBaseName}-stacks.txt")
            diff.parentFile.mkdirs()
            if (negate) {
                flameGraphGenerator.generateDiff(underTestStacks, baselineStacks, diff)
            } else {
                flameGraphGenerator.generateDiff(baselineStacks, underTestStacks, diff)
            }
            return diff
        }
        return null
    }

    private static String stacksBasename(File underTestStacks, EventType type, DetailLevel level) {
        underTestStacks.name - "${postFixFor(type, level)}-stacks.txt"
    }

    private static File stacksFileName(File baseDir, EventType type, DetailLevel level) {
        baseDir.listFiles().find  {it.name.endsWith("${postFixFor(type, level)}-stacks.txt")}
    }

    private static String postFixFor(EventType type, DetailLevel level) {
        "-${type.id}-${level.name().toLowerCase(Locale.ROOT)}"
    }

    private void generateDifferentialFlameGraph(File stacks, EventType type, DetailLevel level, boolean negate) {
        File flames = new File(stacks.parentFile, stacks.name.replace("-stacks.txt", "-flames.svg"))
        List<String> options = ["--title", type.displayName + "${negate ? " Forward " : " Backward "}Differential Flame Graph", "--countname", type.unitOfMeasure] + level.flameGraphOptions
        if (negate) {
            options << "--negate"
        }
        flameGraphGenerator.generateFlameGraph(stacks, flames, options as String[])
        flames
    }

    private void generateDifferentialIcicleGraph(File stacks, EventType type, DetailLevel level, boolean negate) {
        File icicles = new File(stacks.parentFile, stacks.name.replace("-stacks.txt", "-icicles.svg"))
        List<String> options = ["--title", type.displayName + "${negate ? " Forward " : " Backward "}Differential Icicle Graph", "--countname", type.unitOfMeasure, "--reverse", "--invert"] + level.icicleGraphOptions
        if (negate) {
            options << "--negate"
        }
        flameGraphGenerator.generateFlameGraph(stacks, icicles, options as String[])
        icicles
    }

    enum EventType {
        CPU("cpu", "CPU", "samples"),
        ALLOCATION("allocation", "Allocation size", "kB"),
        MONITOR_BLOCKED("monitor-blocked", "Java Monitor Blocked", "ms"),
        IO("io", "File and Socket IO", "ms");

        private final String id
        private final String displayName
        private final String unitOfMeasure

        EventType(String id, String displayName, String unitOfMeasure) {
            this.id = id
            this.displayName = displayName
            this.unitOfMeasure = unitOfMeasure
        }
    }

    enum DetailLevel {
        RAW(['--minwidth', '0.5'], ['--minwidth', '1']),
        SIMPLIFIED(['--minwidth', '1'], ['--minwidth', '2'])

        private List<String> flameGraphOptions
        private List<String> icicleGraphOptions

        DetailLevel(List<String> flameGraphOptions, List<String> icicleGraphOptions) {
            this.flameGraphOptions = flameGraphOptions
            this.icicleGraphOptions = icicleGraphOptions
        }

        List<String> getFlameGraphOptions() {
            return flameGraphOptions
        }

        List<String> getIcicleGraphOptions() {
            return icicleGraphOptions
        }
    }
}
