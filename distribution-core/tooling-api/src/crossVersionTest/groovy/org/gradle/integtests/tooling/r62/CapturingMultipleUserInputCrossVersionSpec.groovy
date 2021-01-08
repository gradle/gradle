/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.integtests.tooling.r62

import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.tooling.ProjectConnection

@TargetGradleVersion(">=6.2")
class CapturingMultipleUserInputCrossVersionSpec extends ToolingApiSpecification {
    private static final String DUMMY_TASK_NAME = 'doSomething'

    private static final QuestionAndAnswerSpec FOO = askQuestion('Foo?', 'yes')
    private static final QuestionAndAnswerSpec BAR = askQuestion('Bar?', 'no')

    def outputStream = new ByteArrayOutputStream()

    def setup() {
        file('buildSrc/src/main/java/MultipleUserInputPlugin.java') << """
            import org.gradle.api.Project;
            import org.gradle.api.Plugin;

            import org.gradle.api.internal.project.ProjectInternal;
            import org.gradle.api.internal.tasks.userinput.UserInputHandler;

            public class MultipleUserInputPlugin implements Plugin<Project> {
                @Override
                public void apply(Project project) {
                    UserInputHandler userInputHandler = ((ProjectInternal) project).getServices().get(UserInputHandler.class);
                    String fooAnswer = userInputHandler.askQuestion("${FOO.question}", "${FOO.defaultAnswer}");
                    System.out.println("${FOO.answerPrefix} " + fooAnswer);

                    String barAnswer = userInputHandler.askQuestion("${BAR.question}", "${BAR.defaultAnswer}");
                    System.out.println("${BAR.answerPrefix} " + barAnswer);
                }
            }
        """

        file('build.gradle') << """
            apply plugin: MultipleUserInputPlugin
            
            task $DUMMY_TASK_NAME
        """
    }

    def "can capture multiple user input if standard input was provided"() {
        when:
        withConnection { ProjectConnection connection ->
            runBuildWithStandardInput(connection, answers('something one', 'something two'))
        }

        then:
        output.contains(FOO.prompt)
        output.contains(FOO.answerOutput('something one'))
        output.contains(BAR.prompt)
        output.contains(BAR.answerOutput('something two'))
    }

    def "can capture multiple user input if standard input was provided using default values"() {
        when:
        withConnection { ProjectConnection connection ->
            runBuildWithStandardInput(connection, answers('', ''))
        }

        then:
        output.contains(FOO.prompt)
        output.contains(FOO.answerOutput())
        output.contains(BAR.prompt)
        output.contains(BAR.answerOutput())
    }

    def "can default subsequent user input as default values if standard input was provided"() {
        when:
        withConnection { ProjectConnection connection ->
            runBuildWithStandardInput(connection, answers('something'))
        }

        then:
        output.contains(FOO.prompt)
        output.contains(FOO.answerOutput('something'))
        output.contains(BAR.prompt)
        output.contains(BAR.answerOutput())
    }

    private void runBuildWithStandardInput(ProjectConnection connection, String answers) {
        def build = connection.newBuild()
        build.standardOutput = outputStream
        build.forTasks(DUMMY_TASK_NAME)
        build.standardInput = new ByteArrayInputStream(answers.bytes)
        build.run()
    }

    static String answers(String... answers) {
        return answers.collect { it + System.getProperty('line.separator') }.join('')
    }

    private String getOutput() {
        outputStream.toString()
    }

    private static QuestionAndAnswerSpec askQuestion(String question, String defaultAnswer) {
        return new QuestionAndAnswerSpec() {
            @Override
            String getQuestion() {
                return question
            }

            @Override
            String getDefaultAnswer() {
                return defaultAnswer
            }
        }
    }

    private static abstract class QuestionAndAnswerSpec {
        abstract String getQuestion()

        abstract String getDefaultAnswer()

        String getPrompt() {
            return "$question (default: $defaultAnswer)"
        }

        String getAnswerPrefix() {
            return "Your answer to '$question' was:"
        }

        String answerOutput(String answer = defaultAnswer) {
            return "$answerPrefix $answer"
        }
    }
}
