/*
 * Copyright 2009 the original author or authors.
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

import org.junit.Ignore
import org.junit.Test
import org.gradle.integtests.fixtures.AbstractIntegrationTest

import static org.gradle.integtests.fixtures.executer.TaskOrderSpecs.any

class TaskAutoDependencyIntegrationTest extends AbstractIntegrationTest {
    @Test
    public void autoAddsInputFileCollectionAsADependency() {
        // Include a configuration with transitive dep on a Jar and an unmanaged Jar.

        testFile('settings.gradle') << 'include "a", "b"'
        testFile('a/build.gradle') << '''
configurations { compile }
dependencies { compile project(path: ':b', configuration: 'archives') }

task doStuff(type: InputTask) {
    src = configurations.compile + fileTree('src/java')
}

class InputTask extends DefaultTask {
    @InputFiles
    def FileCollection src
}
'''
        testFile('b/build.gradle') << '''
apply plugin: 'base'
task jar {
    doLast {
        file('b.jar').text = 'some jar'
    }
}

task otherJar(type: Jar) {
    destinationDir = buildDir
}

configurations { archives }
dependencies { archives files('b.jar') { builtBy jar } }
artifacts { archives otherJar }
'''
        inTestDirectory().withTasks('doStuff').run()
            .assertTasksExecutedInOrder(any(':b:jar', ':b:otherJar'), ':a:doStuff')
    }

    @Test @Ignore
    public void addsDependenciesForInheritedConfiguration() {
        fail()
    }

    @Test @Ignore
    public void addsDependenciesForFileCollectionInSameProject() {
        fail()
    }

    @Test @Ignore
    public void addsDependenciesForFileCollectionInProjectWithNoArtifacts() {
        fail()
    }
}
