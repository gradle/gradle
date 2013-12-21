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
package org.gradle.plugins.ide.eclipse;


import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.AbstractSpockTaskTest
import org.gradle.plugins.ide.eclipse.model.EclipseClasspath

public class GenerateEclipseClasspathTest extends AbstractSpockTaskTest {

    private GenerateEclipseClasspath eclipseClasspath;

    ConventionTask getTask() {
        return eclipseClasspath
    }

    def setup() {
        eclipseClasspath = createTask(GenerateEclipseClasspath.class);
        eclipseClasspath.classpath = new EclipseClasspath()
    }

    def containers_shouldAdd() {
        when:
        eclipseClasspath.containers "container1"
        eclipseClasspath.containers "container2"

        then:
        eclipseClasspath.containers == ['container1', 'container2'] as Set
    }

    def variables_shouldAdd() {
        when:
        eclipseClasspath.variables variable1: 'value1'
        eclipseClasspath.variables variable2: 'value2'

        then:
        eclipseClasspath.variables == [variable1: 'value1', variable2: 'value2']
    }
}
