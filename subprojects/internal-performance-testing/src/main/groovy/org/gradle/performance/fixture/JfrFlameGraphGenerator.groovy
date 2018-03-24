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
    private final FlameGraphSanitizer flameGraphSanitizer = new FlameGraphSanitizer(new FlameGraphSanitizer.RegexBasedSanitizerFunction(
        (~'build_([a-z0-9]+)'): 'build script',
        (~'settings_([a-z0-9]+)'): 'settings script',
        (~'.*BuildOperation.*'): 'build operations',
        (~'.*(Execut[eo]r|Execution).*(execute|run|proceed).*'): 'execution infrastructure',
        (~'.*(PluginManager|ObjectConfigurationAction|PluginTarget|PluginAware|Script.apply|ScriptPlugin|ScriptTarget|ScriptRunner).*'): 'plugin management',
        (~'.*(DynamicObject|Closure.call|MetaClass|MetaMethod|CallSite|ConfigureDelegate|Method.invoke|MethodAccessor|Proxy|ConfigureUtil|Script.invoke|ClosureBackedAction).*'): 'dynamic invocation',
        (~'.*(ProjectEvaluator|Project.evaluate).*'): 'project evaluation',
    ))
    private FlameGraphGenerator flameGraphGenerator = new FlameGraphGenerator()

    void generateGraphs(File jfrRecording) {
        EventType.values().each { EventType type ->
            def stacks = generatedRawStacks(jfrRecording, type);
            def sanitizedStacks = generateSimplifiedStacks(jfrRecording, type)
            generateFlameGraphs(stacks, type)
            generateFlameGraphs(sanitizedStacks, type)
        }
    }

    private File generatedRawStacks(File jfrRecording, EventType type) {
        File stacks = new File(jfrRecording.parentFile, "${type.id}/raw/stacks.txt")
        stacksConverter.convertToStacks(jfrRecording, stacks, "-e", type.id)
        stacks
    }

    private File generateSimplifiedStacks(File jfrRecording, EventType type) {
        File stacks = File.createTempFile("stacks", ".txt")
        stacksConverter.convertToStacks(jfrRecording, stacks, "-ha", "-i", "-sn", "-e", type.id)
        File simplified = new File(jfrRecording.parentFile, "${type.id}/simplified/stacks.txt")
        flameGraphSanitizer.sanitize(stacks, simplified)
        stacks.delete()
        simplified
    }

    private void generateFlameGraphs(File stacks, EventType type) {
        generateFlameGraph(stacks, type)
        generateIcicleGraph(stacks, type)
    }

    private void generateFlameGraph(File stacks, EventType type) {
        File flames = new File(stacks.parentFile, "flames.svg")
        flameGraphGenerator.generate(stacks, flames, "--minwidth", "1", "--title", "${type.displayName} Flame Graph", "--countname", type.unitOfMeasure)
        flames
    }

    private void generateIcicleGraph(File stacks, EventType type) {
        File icicles = new File(stacks.parentFile, "icicles.svg")
        flameGraphGenerator.generate(stacks, icicles, "--minwidth", "2", "--reverse", "--invert", "--colors", "blue", "--title", "${type.displayName} Icicle Graph", "--countname", type.unitOfMeasure)
        icicles
    }

    private static enum EventType {
        CPU("cpu", "CPU", "samples"),
        ALLOCATION("allocation-tlab", "Allocation in new TLAB", "bytes"),
        MONITOR_BLOCKED("monitor-blocked", "Java Monitor Blocked", "samples");

        private final String id
        private final String displayName
        private final String unitOfMeasure

        private EventType(String id, String displayName, String unitOfMeasure) {
            this.unitOfMeasure = unitOfMeasure
            this.displayName = displayName
            this.id = id
        }
    }

}
