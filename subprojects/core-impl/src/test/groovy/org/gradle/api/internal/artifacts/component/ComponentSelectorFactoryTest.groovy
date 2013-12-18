/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.internal.artifacts.component

import org.gradle.api.artifacts.component.ComponentIdentifier
import org.gradle.api.artifacts.component.ComponentSelector
import spock.lang.Specification

class ComponentSelectorFactoryTest extends Specification {
    def "Creates project component selector from identifier"() {
        when:
        ComponentIdentifier componentIdentifier = new DefaultProjectComponentIdentifier(':myPath')
        ComponentSelector componentSelector = ComponentSelectorFactory.instance.createSelector(componentIdentifier)

        then:
        componentSelector instanceof DefaultProjectComponentSelector
        ((DefaultProjectComponentSelector)componentSelector).projectPath == ':myPath'
    }

    def "Creates module component selector from identifier"() {
        when:
        ComponentIdentifier componentIdentifier = new DefaultModuleComponentIdentifier('some-group', 'some-name', 'some-version')
        ComponentSelector componentSelector = ComponentSelectorFactory.instance.createSelector(componentIdentifier)

        then:
        componentSelector instanceof DefaultModuleComponentSelector
        ((DefaultModuleComponentSelector)componentSelector).group == 'some-group'
        ((DefaultModuleComponentSelector)componentSelector).module == 'some-name'
        ((DefaultModuleComponentSelector)componentSelector).version == 'some-version'
    }
}
