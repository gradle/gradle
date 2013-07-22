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
package org.gradle.plugins.ide.eclipse

import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.AbstractSpockTaskTest
import org.gradle.plugins.ide.eclipse.model.EclipseWtpComponent
import org.gradle.plugins.ide.eclipse.model.WbProperty
import org.gradle.plugins.ide.eclipse.model.WbResource

public class GenerateEclipseWtpComponentTest extends AbstractSpockTaskTest {
    private eclipseComponent = createTask(GenerateEclipseWtpComponent)

    def setup() {
        eclipseComponent.component = new EclipseWtpComponent(project, null)
    }

    ConventionTask getTask() { eclipseComponent }

    def "property should add"() {
        when:
        eclipseComponent.property name: 'prop1', value: 'value1'
        eclipseComponent.property name: 'prop2', value: 'value2'

        then:
        eclipseComponent.properties == [new WbProperty('prop1', 'value1'), new WbProperty('prop2', 'value2')]
    }

    def "resource should add"() {
        when:
        eclipseComponent.resource deployPath: 'path1', sourcePath: 'sourcepath1'
        eclipseComponent.resource deployPath: 'path2', sourcePath: 'sourcepath2'

        then:
        eclipseComponent.resources == [new WbResource('path1', 'sourcepath1'), new WbResource('path2', 'sourcepath2')]
    }

    def "variables should add"() {
        when:
        eclipseComponent.variables variable1: 'value1'
        eclipseComponent.variables variable2: 'value2'

        then:
        eclipseComponent.variables == [variable1: 'value1', variable2: 'value2']
    }
}
