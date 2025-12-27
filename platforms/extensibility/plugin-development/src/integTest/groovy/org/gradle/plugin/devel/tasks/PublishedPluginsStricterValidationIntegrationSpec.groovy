/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.plugin.devel.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.internal.reflect.validation.ValidationMessageChecker

class PublishedPluginsStricterValidationIntegrationSpec extends AbstractIntegrationSpec implements ValidationMessageChecker, ValidatePluginsTrait {

    def "adding a publishing plugin enables stricter validation (#publishingPlugin)"() {
        given:
        groovyTaskSource << """
            import org.gradle.api.*
            import org.gradle.api.tasks.*
            import org.gradle.work.*

            @DisableCachingByDefault(because = "test task")
            class MyTask extends DefaultTask {
                @InputFile
                File fileProp
            }
        """

        expect:
        assertValidationSucceeds()

        when:
        buildFile.text = """
        plugins {
            $publishingPlugin
        }
        ${buildFile.text}
        """

        then:
        assertValidationFailsWith([
            error(missingNormalizationStrategyConfig { type('MyTask').property('fileProp').annotatedWith('InputFile') }, 'validation_problems', 'missing_normalization_annotation'),
        ])

        and:
        verifyAll(receivedProblem(0)) {
            fqid == 'validation:property-validation:missing-normalization-annotation'
            contextualLabel == 'Type \'MyTask\' property \'fileProp\' is annotated with @InputFile but missing a normalization strategy'
            details == 'If you don\'t declare the normalization, outputs can\'t be re-used between machines or locations on the same machine, therefore caching efficiency drops significantly'
            solutions == ['Declare the normalization strategy by annotating the property with either @PathSensitive, @Classpath or @CompileClasspath']
            additionalData.asMap == [
                'typeName': 'MyTask',
                'propertyName': 'fileProp',
            ]
        }

        where:
        publishingPlugin                                  | _
        "id('maven-publish')"                             | _
        "id('ivy-publish')"                               | _
        "id('com.gradle.plugin-publish') version '2.0.0'" | _
    }
}
