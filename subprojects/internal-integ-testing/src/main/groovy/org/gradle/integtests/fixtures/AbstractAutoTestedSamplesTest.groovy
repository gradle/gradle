/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter

class AbstractAutoTestedSamplesTest extends AbstractIntegrationTest {

    def util = new AutoTestedSamplesUtil()

    void runSamplesFrom(String dir) {
        util.findSamples(dir) { file, sample, tagSuffix ->
            println "Found sample: ${sample.split("\n")[0]} (...) in $file"
            if (tagSuffix.contains('WithoutCC') && GradleContextualExecuter.configCache) {
                println 'Skipping sample tagged WithoutCC'
                return
            }
            def buildFile = testFile('build.gradle')
            def settingsFile = testFile('settings.gradle')
            def fileToTest = tagSuffix.contains('Settings') ? settingsFile : buildFile
            if (tagSuffix.contains('WithDeprecations')) {
                executer.noDeprecationChecks()
            }
            fileToTest.text = sample
            executer
                .withTasks('help')
                .withArguments("--stacktrace")
            beforeSample(file, tagSuffix)
            executer.run()
            fileToTest.delete()
        }
    }

    protected void beforeSample(File file, String tagSuffix) {
        // default is no-op
    }

    /**
     * Useful for quick dev cycles when you need to run test against a single file.
     *
     * @param includes ant-like includes, e.g. '**\SomeClass.java'
     */
    void includeOnly(String includes) {
        util.includes = includes
    }
}
