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

package org.gradle.test.fixtures.plugin

import org.gradle.language.base.internal.LanguageSourceSetFactory
import org.gradle.model.internal.type.ModelType
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Unroll

abstract class AbstractLanguagePluginSpec extends Specification {
    final def project = TestUtil.createRootProject()

    abstract Class getPluginClass()

    abstract Class getLanguageSourceSet()

    abstract String getLanguageId()

    @Unroll
    def "registers #language in language registration"() {
        when:
        project.pluginManager.apply(pluginClass)
        project.evaluate()


        then:
        def languageRegistrations = project.modelRegistry.realize("languageSourceSetFactory", LanguageSourceSetFactory).registrations
        def languageRegistration = languageRegistrations.find { it.name == language }

        languageRegistration != null
        languageRegistration.sourceSetType == ModelType.of(languageSourceSet)

        where:
        language = languageId
    }

}
