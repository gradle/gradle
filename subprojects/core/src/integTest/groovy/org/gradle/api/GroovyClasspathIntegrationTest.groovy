/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class GroovyClasspathIntegrationTest extends AbstractIntegrationSpec {

    def "script can use io extensions"() {
        buildFile """
            tasks.register('show') {
              outputs.dir('build/test')
              def projectDir = projectDir
              doLast {
                      // Now setup the writer
                      def pw = new File(projectDir, "build/test/test-file.txt").toPath().newPrintWriter('UTF-8')
              }
            }
        """

        expect:
        succeeds("show")
    }

    def "script can use dateutil extensions"() {
        buildFile """
            tasks.register('show') {
              doLast {
                      println(new Date().format("DD-MM-YYYY"))
              }
            }
        """

        expect:
        succeeds("show")
    }

}
