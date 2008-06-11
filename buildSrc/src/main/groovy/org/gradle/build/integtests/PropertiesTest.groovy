/*
 * Copyright 2007 the original author or authors.
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
 
package org.gradle.build.integtests

/**
 * @author Hans Dockter
 */
class PropertiesTest {
    static void execute(String gradleHome, String samplesDirName, String userguideOutputDir) {
        String output = Executer.execute(gradleHome, "$samplesDirName/properties", ['-PcommandLineProjectProp=commandLineProjectPropValue -Dorg.gradle.project.systemProjectProp=systemPropertyValue printProps'], ['ORG_GRADLE_PROJECT_envProjectProp=envPropertyValue'], '', Executer.QUIET).output
        String expectedOutput = expectedOutput(new File(userguideOutputDir, 'properties.out'))
        assert expectedOutput == output
    }

    static void main(String[] args) {
        execute(args[0], args[1])
    }

    static String expectedOutput(File file) {
        String nl = System.properties['line.separator']
        List lines = file.readLines()
        lines[1..lines.size()-1].join(nl) + nl
    }
}
