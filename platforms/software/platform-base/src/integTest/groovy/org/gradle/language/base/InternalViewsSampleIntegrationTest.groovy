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


import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache
import org.gradle.test.precondition.Requires
import org.gradle.test.preconditions.UnitTestPreconditions
import org.junit.Rule

@Requires(UnitTestPreconditions.Online)
@UnsupportedWithConfigurationCache(because = "software model")
class InternalViewsSampleIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    Sample internalViewsSample = new Sample(temporaryFolder, "customModel/internalViews/groovy")

    // NOTE If you change this, you'll also need to change docs/src/doc/snippets/customModel/languageType/groovy/softwareModelExtend-iv-model.out
    def "show mutated public view data but no internal view data in model report"() {
        given:
        sample internalViewsSample
        when:
        succeeds "model"
        then:
        println output
        output.contains """
            + components
                  | Type:   \torg.gradle.platform.base.ComponentSpecContainer
                  | Creator: \tComponentBasePlugin.PluginRules#components(ComponentSpecContainer)
                  | Rules:
                     ⤷ components { ... } @ build.gradle line 37, column 5
                     ⤷ MyPlugin#mutateMyComponents(ModelMap<MyComponentInternal>)
                + my
                      | Type:   \tMyComponent
                      | Creator: \tcomponents { ... } @ build.gradle line 37, column 5 > create(my)
                      | Rules:
                         ⤷ MyPlugin#mutateMyComponents(ModelMap<MyComponentInternal>) > all()
                    + publicData
                          | Type:   \tjava.lang.String
                          | Value:  \tSome PUBLIC data
                          | Creator: \tcomponents { ... } @ build.gradle line 37, column 5 > create(my)
            """.stripIndent().trim()
    }
}
