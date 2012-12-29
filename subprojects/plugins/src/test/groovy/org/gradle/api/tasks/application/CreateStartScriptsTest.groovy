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

import spock.lang.Specification
import org.gradle.util.HelperUtil

class CreateStartScriptsTest extends Specification {
    final CreateStartScripts task = HelperUtil.createTask(CreateStartScripts.class)

    def scriptNameDefaultsToApplicationName() {
        task.outputDir = new File('output')

        when:
        task.applicationName = "myApp"

        then:
        task.unixScript == new File(task.outputDir, 'myApp')
        task.windowsScript == new File(task.outputDir, 'myApp.bat')
    }
    
    def optsEnvironmentVariableNameDefaultsToApplicationName() {
        when:
        task.applicationName = null

        then:
        task.optsEnvironmentVar == null

        when:
        task.applicationName = "myApp"

        then:
        task.optsEnvironmentVar == 'MY_APP_OPTS'

        when:
        task.optsEnvironmentVar = 'APP_OPTS'

        then:
        task.optsEnvironmentVar == 'APP_OPTS'
    }

    def exitEnvironmentVariableNameDefaultsToApplicationName() {
        when:
        task.applicationName = null

        then:
        task.exitEnvironmentVar == null

        when:
        task.applicationName = "myApp"

        then:
        task.exitEnvironmentVar == 'MY_APP_EXIT_CONSOLE'

        when:
        task.exitEnvironmentVar = 'APP_EXIT'

        then:
        task.exitEnvironmentVar == 'APP_EXIT'
    }

    def defaultJvmOptsDefaultsToEmptyList() {
        when:
        task.defaultJvmOpts = null

        then:
        task.defaultJvmOpts == []
    }
}
