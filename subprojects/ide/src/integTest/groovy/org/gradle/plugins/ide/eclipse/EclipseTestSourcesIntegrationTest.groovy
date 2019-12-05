/*
 * Copyright 2019 the original author or authors.
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
package org.gradle.plugins.ide.eclipse

import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import org.junit.Test

class EclipseTestSourcesIntegrationTest extends AbstractEclipseIntegrationTest {

    @Test
    @ToBeFixedForInstantExecution
    void "All test source folders and test dependencies are marked with test attribute"() {
        //when
        file('src/main/java').mkdirs()
        file('src/test/java').mkdirs()
        runEclipseTask """
apply plugin: 'java'
apply plugin: 'eclipse'

repositories.jcenter()

dependencies {
     implementation "com.google.guava:guava:21.0"
     testImplementation "junit:junit:4.12"
}

"""

        //then
        classpath.lib("guava-21.0.jar").assertHasNoAttribute("test", "true")
        classpath.lib("junit-4.12.jar").assertHasAttribute("test", "true")
        classpath.sourceDir("src/main/java").assertHasNoAttribute("test", "true")
        classpath.sourceDir("src/test/java").assertHasAttribute("test", "true")
    }
}
