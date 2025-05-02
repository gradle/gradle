/*
 * Copyright 2015 the original author or authors.
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
package org.gradle.api.internal.artifacts.ivyservice.moduleconverter.dependencies

import org.gradle.api.artifacts.ModuleIdentifier
import org.gradle.api.internal.artifacts.DefaultExcludeRule
import org.gradle.api.internal.artifacts.ImmutableModuleIdentifierFactory
import org.gradle.internal.component.model.Exclude
import spock.lang.Issue
import spock.lang.Specification

class DefaultExcludeRuleConverterTest extends Specification {

    def moduleIdentifierFactory = Mock(ImmutableModuleIdentifierFactory) {
        module(_,_) >> { args ->
            Mock(ModuleIdentifier) {
                getGroup() >> args[0]
                getName() >> args[1]
            }
        }
    }

    @Issue("gradle/gradle#951")
    def "can create exclude rule for configuration name '#configurationName'"() {
        given:
        def group = 'someOrg'
        def module = 'someModule'

        when:
        Exclude exclude = new DefaultExcludeRuleConverter(moduleIdentifierFactory).convertExcludeRule(new DefaultExcludeRule(group, module))

        then:
        exclude.getModuleId().getGroup() == group
        exclude.getModuleId().getName() == module
        exclude.getArtifact() == null
        exclude.getMatcher() == null

        where:
        configurationName | configurations
        'someConf'        | Collections.singleton('someConf')
        null              | Collections.emptySet()
        ''                | Collections.emptySet()
    }
}
