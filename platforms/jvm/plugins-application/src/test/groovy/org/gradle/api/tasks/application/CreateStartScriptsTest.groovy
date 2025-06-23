/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.api.tasks.application

import org.gradle.api.internal.plugins.UnixStartScriptGenerator
import org.gradle.api.internal.plugins.WindowsStartScriptGenerator
import org.gradle.test.fixtures.AbstractProjectBuilderSpec
import org.gradle.util.TestUtil

class CreateStartScriptsTest extends AbstractProjectBuilderSpec {
    final CreateStartScripts task = TestUtil.create(temporaryFolder).task(CreateStartScripts.class)

    def "uses default start script generators"() {
        expect:
        task.unixStartScriptGenerator instanceof UnixStartScriptGenerator
        task.windowsStartScriptGenerator instanceof WindowsStartScriptGenerator
    }

    def scriptNameDefaultsToApplicationName() {
        task.outputDir.set(new File('output'))

        when:
        task.getApplicationName().set("myApp")

        then:
        task.unixScript.asFile.get() == new File(task.outputDir.asFile.get(), 'myApp')
        task.windowsScript.asFile.get() == new File(task.outputDir.asFile.get(), 'myApp.bat')
    }

    def optsEnvironmentVariableNameDefaultsToApplicationName() {
        when:
        task.getApplicationName().set(null)

        then:
        task.optsEnvironmentVar.getOrNull() == null

        when:
        task.getApplicationName().set("myApp")

        then:
        task.optsEnvironmentVar.get() == 'MY_APP_OPTS'

        when:
        task.optsEnvironmentVar.set('APP_OPTS')

        then:
        task.optsEnvironmentVar.get() == 'APP_OPTS'
    }

    def exitEnvironmentVariableNameDefaultsToApplicationName() {
        when:
        task.getApplicationName().set(null)

        then:
        task.exitEnvironmentVar.getOrNull() == null

        when:
        task.getApplicationName().set("myApp")

        then:
        task.exitEnvironmentVar.get() == 'MY_APP_EXIT_CONSOLE'

        when:
        task.exitEnvironmentVar.set('APP_EXIT')

        then:
        task.exitEnvironmentVar.get() == 'APP_EXIT'
    }
}
