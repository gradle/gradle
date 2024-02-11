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

package org.gradle.integtests.fixtures

import org.gradle.integtests.fixtures.executer.GradleHandle

import static org.gradle.util.internal.TextUtil.getPlatformLineSeparator

final class BuildScanUserInputFixture {

    public static final String DUMMY_TASK_NAME = 'doSomething'
    public static final String QUESTION = "Accept license?"
    public static final String YES = 'yes'
    public static final String NO = 'no'
    public static final byte EOF = 4
    public static final String PROMPT = "$QUESTION [$YES, $NO]"
    private static final String ANSWER_PREFIX = 'License accepted:'

    private BuildScanUserInputFixture() {}

    static String buildScanPlugin() {
        """
            import org.gradle.api.Project;
            import org.gradle.api.Plugin;

            import org.gradle.api.internal.project.ProjectInternal;
            import org.gradle.api.internal.tasks.userinput.BuildScanUserInputHandler;

            public class BuildScanPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    BuildScanUserInputHandler userInputHandler = ((ProjectInternal) project).getServices().get(BuildScanUserInputHandler.class);
                    Boolean accepted = userInputHandler.askYesNoQuestion("$QUESTION");
                    System.out.println("$ANSWER_PREFIX " + accepted);
                }
            }
        """
    }

    static String buildScanPluginApplication() {
        """
            apply plugin: BuildScanPlugin

            task $DUMMY_TASK_NAME
        """
    }

    static String answerOutput(Boolean answer) {
        "$ANSWER_PREFIX $answer"
    }

    static void writeToStdInAndClose(GradleHandle gradleHandle, byte[] input) {
        gradleHandle.stdinPipe.write(input)
        writeLineSeparatorToStdInAndClose(gradleHandle)
    }

    static void writeToStdInAndClose(GradleHandle gradleHandle, byte input) {
        writeToStdInAndClose(gradleHandle, [input] as byte[])
    }

    static void writeToStdInAndClose(GradleHandle gradleHandle, String input) {
        writeToStdInAndClose(gradleHandle, input.bytes)
    }

    private static void writeLineSeparatorToStdInAndClose(GradleHandle gradleHandle) {
        gradleHandle.stdinPipe.write(getPlatformLineSeparator().bytes)
        gradleHandle.stdinPipe.flush()
        gradleHandle.stdinPipe.close()
    }

    static void expectRenderedPromptAndAnswer(GradleHandle gradleHandle, Boolean expectedAnswer) {
        expectRenderedPrompt(gradleHandle, true)
        expectRenderedAnswer(gradleHandle, expectedAnswer)
    }

    static void expectNoPromptAndNullAnswer(GradleHandle gradleHandle) {
        expectRenderedPrompt(gradleHandle, false)
        expectRenderedAnswer(gradleHandle, null)
    }

    private static void expectRenderedPrompt(GradleHandle gradleHandle, boolean rendered) {
        assert gradleHandle.standardOutput.contains(PROMPT) == rendered
    }

    private static void expectRenderedAnswer(GradleHandle gradleHandle, Boolean answer) {
        assert gradleHandle.standardOutput.contains(answerOutput(answer))
    }
}
