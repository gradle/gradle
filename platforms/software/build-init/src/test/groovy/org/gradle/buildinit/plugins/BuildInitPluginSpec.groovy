/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.buildinit.plugins


import org.gradle.buildinit.tasks.InitBuild
import org.gradle.initialization.SettingsState
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class BuildInitPluginSpec extends AbstractProjectBuilderSpec {
    def setup() {
        project.gradle.attachSettings(Stub(SettingsState))
    }

    def "adds 'init' task"() {
        when:
        project.pluginManager.apply BuildInitPlugin

        then:
        project.tasks.init instanceof InitBuild
        project.tasks.init.group == "Build Setup"
    }
}
