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

import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.GradleDistributionExecuter
import org.gradle.util.TestFile
import org.junit.Rule
import org.junit.Test

class IncrementalGroovyProjectBuildIntegrationTest {
    @Rule public final GradleDistribution distribution = new GradleDistribution()
    @Rule public final GradleDistributionExecuter executer = new GradleDistributionExecuter()

    @Test
    public void doesNotRebuildGroovydocIfSourceHasNotChanged() {
        distribution.testFile("src/main/groovy/BuildClass.java") << 'public class BuildClass { }'
        distribution.testFile("build.gradle") << '''
            apply plugin: 'groovy'
            dependencies { groovy localGroovy() }
            groovydoc {
                link('http://download.oracle.com/javase/1.5.0/docs/api', 'java.,org.xml.,javax.,org.xml.')
            }
'''

        executer.withTasks("groovydoc").run();

        TestFile indexFile = distribution.testFile("build/docs/groovydoc/index.html");
        indexFile.assertIsFile();
        TestFile.Snapshot snapshot = indexFile.snapshot();

        executer.withTasks("groovydoc").run().assertTaskSkipped(':groovydoc');

        indexFile.assertHasNotChangedSince(snapshot);

        distribution.testFile("build.gradle").append("groovydoc.link('http://download.oracle.com/javase/1.5.0/docs/api', 'java.')")

        executer.withTasks("groovydoc").run().assertTaskNotSkipped(':groovydoc');

        executer.withTasks("groovydoc").run().assertTaskSkipped(':groovydoc');
    }
}
