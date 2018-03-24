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

    private final File flamegraphScript = createFlameGraphScript()

    private static File createFlameGraphScript() {
        URL flamegraphResource = JfrProfiler.getResource("flamegraph.pl")
        File flamegraphScript = File.createTempFile("flamegraph", ".pl")
        Resources.asByteSource(flamegraphResource).copyTo(Files.asByteSink(flamegraphScript))
        flamegraphScript.deleteOnExit()
        flamegraphScript.setExecutable(true)
        flamegraphScript
    }

    void generate(File stacks, File flames, String... args) {
        def process = ([flamegraphScript.absolutePath, stacks.absolutePath] + args.toList()).execute()
        def fos = flames.newOutputStream()
        process.waitForProcessOutput(fos, System.err)
        fos.close()
    }
}
