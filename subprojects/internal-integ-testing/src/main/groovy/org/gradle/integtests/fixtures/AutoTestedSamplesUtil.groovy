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

import org.gradle.internal.SystemProperties

class AutoTestedSamplesUtil {

    String includes = '**/*.groovy **/*.java'

    void findSamples(String dir, Closure runner) {
        def sources = findDir(dir)
        def ant = new AntBuilder()

        def list = ant.fileScanner {
            fileset(dir: sources, includes: includes)
        }

        list.each() { runSamplesFromFile(it, runner) }
    }

    String findDir(String dir) {
        def workDir = SystemProperties.currentDir
        def candidates = [
            "$workDir/$dir",        //when ran from IDEA
            "$workDir/../../$dir"  //when ran from command line
        ]
        for (c in candidates) {
            if (new File(c).exists()) {
                return c
            }
        }
        throw new RuntimeException("""Couldn't find the root folder :-( Please update the logic so that it detects the root folder correctly.
I tried looking for a root folder here: $candidates
""")
    }

    void runSamplesFromFile(File file, Closure runner) {
        file.text.eachMatch(/(?ms).*?<pre autoTested.*?>(.*?)<\/pre>(.*?)/) {
            def sample = it[1]
            sample = sample.replaceAll(/(?m)^\s*?\*/, '')
            sample = sample.replace('&lt;', '<')
            sample = sample.replace('&gt;', '>')
            sample = sample.replace('&amp;', '&')
            sample = sample.replaceAll(/\{@literal ([^}]+)}/, '$1')
            try {
                runner.call(file, sample)
            } catch (Exception e) {
                throw new RuntimeException("""
*****
Failed to execute sample:
-File: $file
-Sample:
$sample
-Problem: see the full stacktrace below.
*****
""", e);
            }
        }
    }
}
