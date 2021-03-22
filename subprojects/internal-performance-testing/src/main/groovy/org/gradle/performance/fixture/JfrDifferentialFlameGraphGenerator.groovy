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

import com.google.common.base.Joiner
import groovy.transform.CompileStatic
/**
 * Generates flame graphs based on JFR recordings.
 */
@CompileStatic
class JfrDifferentialFlameGraphGenerator implements ProfilerFlameGraphGenerator {

    private FlameGraphGenerator flameGraphGenerator = new FlameGraphGenerator()
    private final OutputDirSelector outputDirSelector

    JfrDifferentialFlameGraphGenerator(OutputDirSelector outputDirSelector) {
        this.outputDirSelector = outputDirSelector
    }

    @Override
    void generateDifferentialGraphs(String testId) {
        def baseOutputDir = outputDirSelector.outputDirFor(testId)
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
            String baselineDifference = computeDifferenceOfBaselineToCurrentName(underTestBasename, baselineTestBasename)
            String diffBaseName = "${underTestBasename}-vs-${baselineDifference}-${negate ? "forward-" : "backward-"}diff"
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

    private static String computeDifferenceOfBaselineToCurrentName(String underTestBasename, String baselineTestBasename) {
        List<String> underTestParts = Arrays.asList(underTestBasename.split("-"));
        Deque<String> remainderOfBaseline = new ArrayDeque<>(Arrays.asList(baselineTestBasename.split("-")));

        for (String underTestPart : underTestParts) {
            if (!remainderOfBaseline.isEmpty() && underTestPart == remainderOfBaseline.getFirst()) {
                remainderOfBaseline.removeFirst();
            } else {
                break;
            }
        }

        Collections.reverse(underTestParts);
        for (String underTestPart : underTestParts) {
            if (!remainderOfBaseline.isEmpty() && underTestPart == remainderOfBaseline.getLast()) {
                remainderOfBaseline.removeLast();
            } else {
                break;
            }
        }

        return Joiner.on("-").join(remainderOfBaseline);
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
