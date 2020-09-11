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

package org.gradle.model.managed

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.UnsupportedWithConfigurationCache

@UnsupportedWithConfigurationCache(because = "software model")
class UnmanagedCollectionPropertyIntegrationTest extends AbstractIntegrationSpec {

    def "managed type can have unmanaged properties of collection type with types that are not scalar types"() {
        given:
        buildScript """

        class Widget {
            String name
        }

        @Managed
        interface Container {
            @Unmanaged
            List<Widget> getWidgets()
            void setWidgets(List<Widget> l)

            @Unmanaged
            Set<Widget> getItems()
            void setItems(Set<Widget> s)

            @Unmanaged
            List getRawWidgets()
            void setRawWidgets(List l)
        }

        class Rules extends RuleSource {
            @Model
            void createContainer(Container c) {
                c.items = []
                c.widgets = []
                c.rawWidgets = []
            }

            @Mutate
            void addItems(Container c) {
                c.items.add new Widget()
                c.widgets.add new Widget()
                c.rawWidgets.add new Widget()
            }

            @Mutate
            void addCheckTask(ModelMap<Task> tasks, Container c) {
                tasks.create('check') {
                    assert c.items.size() == 1
                    assert c.widgets.size() == 1
                    assert c.rawWidgets.size() == 1
                }
            }
        }

        apply plugin: Rules
        """

        expect:
        succeeds 'check'
    }
}
