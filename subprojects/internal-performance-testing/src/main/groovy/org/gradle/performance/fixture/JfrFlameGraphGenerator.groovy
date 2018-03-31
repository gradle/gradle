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

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

/**
 * Generates flame graphs based on JFR recordings.
 *
 * TODO create flame graph diffs
 */
@CompileStatic
@PackageScope
class JfrFlameGraphGenerator {

    private JfrToStacksConverter stacksConverter = new JfrToStacksConverter()
    private FlameGraphGenerator flameGraphGenerator = new FlameGraphGenerator()

    void generateGraphs(File jfrRecording) {
        EventType.values().each { EventType type ->
            DetailLevel.values().each { DetailLevel level ->
                def stacks = generateStacks(jfrRecording, type, level)
                generateFlameGraph(stacks, type, level)
                generateIcicleGraph(stacks, type, level)
            }
        }
    }

    private File generateStacks(File jfrRecording, EventType type, DetailLevel level) {
        File stacks = File.createTempFile("stacks", ".txt")
        String[] options = level.stackConversionOptions + ["--event", type.id]
        stacksConverter.convertToStacks(jfrRecording, stacks, options)
        File sanitizedStacks = new File(jfrRecording.parentFile, "${type.id}/${level.name().toLowerCase()}/stacks.txt")
        level.sanitizer.sanitize(stacks, sanitizedStacks)
        stacks.delete()
        sanitizedStacks
    }

    private void generateFlameGraph(File stacks, EventType type, DetailLevel level) {
        File flames = new File(stacks.parentFile, "flames.svg")
        String[] options = ["--title", type.displayName + " Flame Graph", "--countname", type.unitOfMeasure] + level.flameGraphOptions
        flameGraphGenerator.generate(stacks, flames, options)
        flames
    }

    private void generateIcicleGraph(File stacks, EventType type, DetailLevel level) {
        File icicles = new File(stacks.parentFile, "icicles.svg")
        String[] options = ["--title", type.displayName + " Icicle Graph", "--countname", type.unitOfMeasure, "--reverse", "--invert", "--colors", "blue"] + level.icicleGraphOptions
        flameGraphGenerator.generate(stacks, icicles, options)
        icicles
    }

    private static enum EventType {
        CPU("cpu", "CPU", "samples"),
        ALLOCATION("allocation-tlab", "Allocation in new TLAB", "kB"),
        MONITOR_BLOCKED("monitor-blocked", "Java Monitor Blocked", "samples"),
        IO("io", "File and Socket IO", "ms");

        private final String id
        private final String displayName
        private final String unitOfMeasure

        private EventType(String id, String displayName, String unitOfMeasure) {
            this.unitOfMeasure = unitOfMeasure
            this.displayName = displayName
            this.id = id
        }
    }

    private static enum DetailLevel {
        RAW(
            [],
            ["--minwidth", "0.5"],
            ["--minwidth", "1"],
            new FlameGraphSanitizer({ it })
        ),
        SIMPLIFIED(
            ["--hide-arguments", "--ignore-line-numbers", "--use-simple-names"],
            ["--minwidth", "1"],
            ["--minwidth", "2"],
            new FlameGraphSanitizer(
                new FlameGraphSanitizer.RegexBasedSanitizeFunction(
                    (~'build_[a-z0-9]+'): 'build script',
                    (~'settings_[a-z0-9]+'): 'settings script',
                ),
                new FlameGraphSanitizer.ContainmentBasedSanitizeFunction(
                    ['BuildOperation']: 'build operations',
                    ['PluginManager', 'ObjectConfigurationAction', 'PluginTarget', 'PluginAware', 'Script.apply', 'ScriptPlugin', 'ScriptTarget', 'ScriptRunner']: 'plugin management',
                    ['DynamicObject', 'Closure.call', 'MetaClass', 'MetaMethod', 'CallSite', 'ConfigureDelegate', 'Method.invoke', 'MethodAccessor', 'Proxy', 'ConfigureUtil', 'Script.invoke', 'ClosureBackedAction', 'getProperty(']: 'dynamic invocation',
                    ['ProjectEvaluator', 'Project.evaluate']: 'project evaluation',
                    ['CommandLine', 'Executer', 'Executor', 'Execution', 'Runner', 'BuildController', 'Bootstrap', 'EntryPoint', 'Main']: 'execution infrastructure',
                )
            )
        )

        private List<String> stackConversionOptions
        private List<String> flameGraphOptions
        private List<String> icicleGraphOptions
        private FlameGraphSanitizer sanitizer

        DetailLevel(List<String> stackConversionOptions, List<String> flameGraphOptions, List<String> icicleGraphOptions, FlameGraphSanitizer sanitizer) {
            this.stackConversionOptions = stackConversionOptions
            this.flameGraphOptions = flameGraphOptions
            this.icicleGraphOptions = icicleGraphOptions
            this.sanitizer = sanitizer
        }

        List<String> getStackConversionOptions() {
            return stackConversionOptions
        }

        List<String> getFlameGraphOptions() {
            return flameGraphOptions
        }

        List<String> getIcicleGraphOptions() {
            return icicleGraphOptions
        }

        FlameGraphSanitizer getSanitizer() {
            return sanitizer
        }
    }

}
