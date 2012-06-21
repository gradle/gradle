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

package org.gradle.integtests

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Issue

class BuildSourceBuilderIntegrationTest extends AbstractIntegrationSpec {


    private static final String EXPECTED_OUTPUT_RUN1 = """:buildSrc:clean UP-TO-DATE
:buildSrc:compileJava UP-TO-DATE
:buildSrc:compileGroovy UP-TO-DATE
:buildSrc:processResources UP-TO-DATE
:buildSrc:classes UP-TO-DATE
:buildSrc:jar
:buildSrc:assemble
:buildSrc:compileTestJava UP-TO-DATE
:buildSrc:compileTestGroovy UP-TO-DATE
:buildSrc:processTestResources UP-TO-DATE
:buildSrc:testClasses UP-TO-DATE
:buildSrc:test
:buildSrc:check
:buildSrc:build
:blocking"""

    private static final EXPECTED_OUTPUT_RUN2 = """:buildSrc:compileJava UP-TO-DATE
:buildSrc:compileGroovy UP-TO-DATE
:buildSrc:processResources UP-TO-DATE
:buildSrc:classes UP-TO-DATE
:buildSrc:jar UP-TO-DATE
:buildSrc:assemble UP-TO-DATE
:buildSrc:compileTestJava UP-TO-DATE
:buildSrc:compileTestGroovy UP-TO-DATE
:buildSrc:processTestResources UP-TO-DATE
:buildSrc:testClasses UP-TO-DATE
:buildSrc:test UP-TO-DATE
:buildSrc:check UP-TO-DATE
:buildSrc:build UP-TO-DATE
:releasing"""


    @Issue("http://issues.gradle.org/browse/GRADLE-2032")
    def "can simultaneously run gradle on projects with buildSrc"() {
        given:
        file("buildSrc").createDir()
        buildFile.text = """
        task blocking << {
            while(!file("block.lock").exists()){
                sleep 10
            }
        }

        task releasing << {
            file("block.lock").createNewFile()
        }
        """
        when:
        def handleRun1 = executer.withTasks("blocking").start()
        handleRun1.waitForStarted()

        def handleRun2 = executer.withTasks("releasing").start()
        and:
        def finish2 = handleRun2.waitForFinish()
        def finish1 = handleRun1.waitForFinish()
        then:
        finish1.error.equals("")
        finish2.error.equals("")
        finish1.output.replaceAll("\r\n","\n").contains(EXPECTED_OUTPUT_RUN1)
        finish2.output.replaceAll("\r\n","\n").contains(EXPECTED_OUTPUT_RUN2)
    }
}