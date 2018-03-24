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
 * TODO maybe create "raw" flame graphs too, for cases when above mentioned things actually regress
 * TODO create memory, IO, locking flame graphs
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
        def stacks = collapseStacks(jfrRecording);
        def sanitizedStacks = sanitizeStacks(stacks)
        generateFlameGraph(sanitizedStacks)
        generateIcicleGraph(sanitizedStacks)
    }

    private File collapseStacks(File jfrRecording) {
        File stacks = new File(jfrRecording.parentFile, "stacks.txt")
        stacksConverter.convertToStacks(jfrRecording, stacks, "-ha", "-i", "-sn")
        stacks
    }

    private File sanitizeStacks(File stacks) {
        File sanitizedStacks = new File(stacks.parentFile, "sanitized-stacks.txt")
        flameGraphSanitizer.sanitize(stacks, sanitizedStacks)
        sanitizedStacks
    }

    private void generateFlameGraph(File sanitizedStacks) {
        File flames = new File(sanitizedStacks.parentFile, "flames.svg")
        flameGraphGenerator.generate(sanitizedStacks, flames, "--minwidth", "1")
        flames
    }

    private void generateIcicleGraph(File sanitizedStacks) {
        File icicles = new File(sanitizedStacks.parentFile, "icicles.svg")
        flameGraphGenerator.generate(sanitizedStacks, icicles, "--minwidth", "2", "--reverse", "--invert", "--colors", "blue")
        icicles
    }

}
