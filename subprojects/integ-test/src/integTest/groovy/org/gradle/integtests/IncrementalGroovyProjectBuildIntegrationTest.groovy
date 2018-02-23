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

import org.gradle.integtests.fixtures.AbstractIntegrationTest
import org.gradle.test.fixtures.file.TestFile
import org.junit.Test

class IncrementalGroovyProjectBuildIntegrationTest extends AbstractIntegrationTest {

    @Test
    void doesNotRebuildGroovydocIfSourceHasNotChanged() {
        file("src/main/groovy/BuildClass.java") << 'public class BuildClass { }'
        file("build.gradle") << '''
            apply plugin: 'groovy'
            dependencies { compile localGroovy() }

            groovydoc {
                link('http://download.oracle.com/javase/1.5.0/docs/api', 'java.,org.xml.,javax.,org.xml.')
            }
'''

        executer.withTasks("groovydoc").run();

        TestFile indexFile = file("build/docs/groovydoc/index.html");
        indexFile.assertIsFile();
        TestFile.Snapshot snapshot = indexFile.snapshot();

        executer.withTasks("groovydoc").run().assertTaskSkipped(':groovydoc');

        indexFile.assertHasNotChangedSince(snapshot);

        file("build.gradle").append("groovydoc.link('http://download.oracle.com/javase/1.5.0/docs/api', 'java.')")

        executer.withTasks("groovydoc").run().assertTaskNotSkipped(':groovydoc');

        executer.withTasks("groovydoc").run().assertTaskSkipped(':groovydoc');
    }
}
