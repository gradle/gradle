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

package org.gradle.integtests.fixtures.internal

/**
 * @author Szczepan Faber, created at: 4/2/11
 */
class AbstractAutoTestedSamplesTest extends AbstractIntegrationTest {

     void runSamplesFrom(String dir) {
        def util = new AutoTestedSamplesUtil()
        util.findSamples(dir) { file, sample ->
            println "Found sample in $file"
            def buildFile = testFile('build.gradle')
            buildFile.text = sample

            usingBuildFile(buildFile).withQuietLogging().withTasks('help').withArguments("-s").run()
        }
    }
}
