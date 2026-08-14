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

package org.gradle.language.base

import org.gradle.api.reporting.model.ModelReportOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.modes.UnsupportedWithConfigurationCache
import org.gradle.platform.base.ApplicationSpec
import org.gradle.platform.base.ComponentSpec
import org.gradle.platform.base.GeneralComponentSpec
import org.gradle.platform.base.LibrarySpec

@UnsupportedWithConfigurationCache(because = "software model")
class BaseModelIntegrationTest extends AbstractIntegrationSpec {

    @Override
    protected void setupExecuter() {
        super.setupExecuter()
    }

    def "empty containers are visible in model report"() {
        buildFile << """
apply plugin: 'component-model-base'
"""

        when:
        expectSoftwareModelDeprecations("org.gradle.component-model-base", "org.gradle.language.base.plugins.LanguageBasePlugin", "org.gradle.platform.base.plugins.BinaryBasePlugin", "org.gradle.platform.base.plugins.ComponentBasePlugin")
        succeeds "model"

        then:
        def reportOutput = ModelReportOutput.from(output)
        reportOutput.hasNodeStructure {
            binaries {
            }
        }
        reportOutput.hasNodeStructure {
            components {
            }
        }
        reportOutput.hasNodeStructure {
            sources {
            }
        }
    }

    def "can declare instance of general type - #componentSpecType"() {
        buildFile << """
            apply plugin: 'component-model-base'
            model {
                components {
                    main(${componentSpecType})
                }
            }
        """

        expect:
        expectSoftwareModelDeprecations("org.gradle.component-model-base", "org.gradle.language.base.plugins.LanguageBasePlugin", "org.gradle.platform.base.plugins.BinaryBasePlugin", "org.gradle.platform.base.plugins.ComponentBasePlugin")
        expectModelDslDeprecation()
        succeeds "model"

        where:
        componentSpecType << [ComponentSpec, GeneralComponentSpec, LibrarySpec, ApplicationSpec]*.simpleName
    }


    private void expectSoftwareModelDeprecations(String... pluginNames) {
        for (String name : pluginNames) {
            executer.expectDocumentedDeprecationWarning("The ${name} plugin has been deprecated. This is scheduled to be removed in Gradle 10. Rule-based/software model plugins are no longer supported. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_software_model")
        }
    }

    private void expectModelDslDeprecation() {
        executer.expectDocumentedDeprecationWarning("The model DSL has been deprecated. This is scheduled to be removed in Gradle 10. Rule-based/software model plugins are no longer supported. Consult the upgrading guide for further information: https://docs.gradle.org/current/userguide/upgrading_version_9.html#deprecated_software_model")
    }
}
