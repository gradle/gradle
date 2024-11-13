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
import org.gradle.integtests.fixtures.StableConfigurationCacheDeprecations
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.platform.base.ApplicationSpec
import org.gradle.platform.base.ComponentSpec
import org.gradle.platform.base.GeneralComponentSpec
import org.gradle.platform.base.LibrarySpec

@UnsupportedWithConfigurationCache(because = "software model")
class BaseModelIntegrationTest extends AbstractIntegrationSpec implements StableConfigurationCacheDeprecations {

    @Override
    protected void setupExecuter() {
        super.setupExecuter()
        expectTaskGetProjectDeprecations()
    }

    def "empty containers are visible in model report"() {
        buildFile << """
apply plugin: 'component-model-base'
"""

        when:
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
        succeeds "model"

        where:
        componentSpecType << [ComponentSpec, GeneralComponentSpec, LibrarySpec, ApplicationSpec]*.simpleName
    }

}
