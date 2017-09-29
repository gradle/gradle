/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.api.internal.tasks.userinput

import spock.lang.Unroll

class BuildScanUserInputHandlerIntegrationTest extends AbstractUserInputHandlerIntegrationTest {

    private static final String YES = 'yes'
    private static final String NO = 'no'

    @Unroll
    def "can ask for license acceptance from plugin and handle input '#input'"() {
        file('buildSrc/src/main/java/BuildScanPlugin.java') << """
            import org.gradle.api.Project;
            import org.gradle.api.Plugin;

            import org.gradle.api.internal.project.ProjectInternal;
            import org.gradle.api.internal.tasks.userinput.BuildScanUserInputHandler;

            public class BuildScanPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    BuildScanUserInputHandler userInputHandler = ((ProjectInternal) project).getServices().get(BuildScanUserInputHandler.class);
                    Boolean accepted = userInputHandler.askYesNoQuestion("Accept license?");
                    System.out.println("License accepted: " + accepted);
                }
            }
        """
        buildFile << """
            apply plugin: BuildScanPlugin
            
            task doSomething
        """
        interactiveExecution()

        when:
        def gradleHandle = executer.withTasks('doSomething').start()

        then:
        writeToStdInAndClose(gradleHandle, stdin)
        gradleHandle.waitForFinish()
        gradleHandle.standardOutput.contains("Accept license? [$YES, $NO]")
        gradleHandle.standardOutput.contains("License accepted: $accepted")

        where:
        input    | stdin     | accepted
        YES      | YES.bytes | true
        NO       | NO.bytes  | false
        'ctrl-d' | EOF       | null
    }
}
