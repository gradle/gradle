/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.integtests.fixtures

import groovy.ant.AntBuilder
import org.gradle.internal.SystemProperties

import java.util.regex.Pattern

class AutoTestedSamplesUtil {

    private static final Pattern SAMPLE_START = Pattern.compile(/<pre class=['"]autoTested(.*?)['"].*?>/)
    private static final Pattern LEADING_ASTERISK_PATTERN = Pattern.compile(/(?m)^\s*?\*/)
    private static final Pattern LITERAL_PATTERN = Pattern.compile(/\{@literal ([^}]+)}/)

    String includes = '**/*.groovy **/*.java'

    void findSamples(String dir, Closure runner) {
        def sources = findDir(dir)
        def ant = new AntBuilder()

        def list = ant.fileScanner {
            fileset(dir: sources, includes: includes)
        }

        list.each() { runSamplesFromFile(it, runner) }
    }

    static String findDir(String dir) {
        def workDir = SystemProperties.instance.currentDir
        def samplesDir = new File("$workDir/$dir")
        if (samplesDir.exists()) {
            assertDeclaredAsInput(samplesDir.absolutePath)
            return samplesDir
        }
        throw new RuntimeException("$samplesDir does not exist")
    }

    static void assertDeclaredAsInput(String dir) {
        String inputs = System.getProperty("declaredSampleInputs")
        assert inputs: "Must declare source directory as input: 'integTest.usesJavadocCodeSnippets.set(true)'"
        assert inputs == dir
    }

    static void runSamplesFromFile(File file, Closure runner) {
        String text = file.text
        def samples = SAMPLE_START.matcher(text)
        while (samples.find()) {
            def tagSuffix = samples.group(1)
            def start = samples.end()
            def end = text.indexOf("</pre>", start)
            def sample = text.substring(start, end)
            sample = LEADING_ASTERISK_PATTERN.matcher(sample).replaceAll('')
            sample = sample.replace('&lt;', '<')
            sample = sample.replace('&gt;', '>')
            sample = sample.replace('&amp;', '&')
            sample = LITERAL_PATTERN.matcher(sample).replaceAll('$1')
            try {
                runner.call(file, sample, tagSuffix)
            } catch (Exception e) {
                throw new RuntimeException("""
*****
Failed to execute sample:
-File: $file
-Sample:
$sample
-Problem: see the full stacktrace below.
*****
""", e)
            }
        }
    }
}
