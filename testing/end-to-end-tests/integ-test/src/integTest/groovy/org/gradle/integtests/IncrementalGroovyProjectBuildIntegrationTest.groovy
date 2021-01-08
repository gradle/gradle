/*
 * Copyright 2010 the original author or authors.
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

class IncrementalGroovyProjectBuildIntegrationTest extends AbstractIntegrationSpec {

    def "does not rebuild Groovydoc if source has not changed"() {
        def indexFile = file("build/docs/groovydoc/index.html");
        file("src/main/groovy/BuildClass.java") << 'public class BuildClass { }'
        buildFile << '''
            apply plugin: 'groovy'
            dependencies { implementation localGroovy() }

            groovydoc {
                link('http://download.oracle.com/javase/1.5.0/docs/api', 'java.,org.xml.,javax.,org.xml.')
            }
        '''

        when:
        run "groovydoc"
        then:
        indexFile.exists()

        when:
        def snapshot = indexFile.snapshot();
        run "groovydoc"
        then:
        skipped ":groovydoc"
        indexFile.assertHasNotChangedSince(snapshot)

        when:
        buildFile << '''
            groovydoc.link('http://download.oracle.com/javase/1.5.0/docs/api', 'java.')
        '''
        run "groovydoc"
        then:
        executedAndNotSkipped ":groovydoc"

        when:
        run "groovydoc"
        then:
        skipped ":groovydoc"
    }
}
