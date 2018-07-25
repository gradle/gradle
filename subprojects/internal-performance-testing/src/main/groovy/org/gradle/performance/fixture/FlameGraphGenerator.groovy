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

import com.google.common.io.Files
import com.google.common.io.Resources
import groovy.transform.CompileStatic
import groovy.transform.PackageScope

/**
 * Generates flame graphs from collapsed stacks.
 */
@CompileStatic
@PackageScope
class FlameGraphGenerator {

    private final File flamegraphScript = createScript("flamegraph")
    private final File diffScript = createScript("difffolded")

    private static File createScript(String scriptName) {
        URL scriptResource = JfrProfiler.getResource(scriptName + ".pl")
        File script = File.createTempFile(scriptName, ".pl")
        Resources.asByteSource(scriptResource).copyTo(Files.asByteSink(script))
        script.deleteOnExit()
        script.setExecutable(true)
        script
    }

    void generateFlameGraph(File stacks, File flames, String... args) {
        def process = ([flamegraphScript.absolutePath, stacks.absolutePath] + args.toList()).execute()
        def fos = flames.newOutputStream()
        process.waitForProcessOutput(fos, System.err)
        fos.close()
    }

    void generateDiff(File baseline, File versionUnderTest, File diff) {
        Files.touch(baseline)
        Files.touch(versionUnderTest)
        def process = [diffScript.absolutePath, baseline.absolutePath, versionUnderTest.absolutePath].execute()
        def fos = diff.newOutputStream()
        process.waitForProcessOutput(fos, System.err)
        fos.close()
    }
}
