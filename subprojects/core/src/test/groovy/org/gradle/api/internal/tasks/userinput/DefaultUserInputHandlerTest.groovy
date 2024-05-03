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

import org.gradle.internal.logging.events.BooleanQuestionPromptEvent
import org.gradle.internal.logging.events.IntQuestionPromptEvent
import org.gradle.internal.logging.events.OutputEventListener
import org.gradle.internal.logging.events.SelectOptionPromptEvent
import org.gradle.internal.logging.events.TextQuestionPromptEvent
import org.gradle.internal.logging.events.UserInputRequestEvent
import org.gradle.internal.logging.events.UserInputResumeEvent
import org.gradle.internal.logging.events.YesNoQuestionPromptEvent
import org.gradle.internal.time.Clock
import spock.lang.Specification
import spock.lang.Subject

import java.util.function.Function

class DefaultUserInputHandlerTest extends Specification {

    private static final String TEXT = 'Accept license?'
    def outputEventBroadcaster = Mock(OutputEventListener)
    def userInputReader = Mock(UserInputReader)
    def clock = Mock(Clock)
    @Subject
    def userInputHandler = new DefaultUserInputHandler(outputEventBroadcaster, clock, userInputReader)

    def "can ask required yes/no question"() {
        when:
        def input = ask { it.askYesNoQuestion("question?") }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { YesNoQuestionPromptEvent event ->
            assert event.question == 'question?'
        }
        1 * userInputReader.readInput() >> new UserInputReader.TextResponse(enteredUserInput)
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == sanitizedUserInput

        where:
        enteredUserInput | sanitizedUserInput
        'yes'            | true
        'no'             | false
    }

    def "required yes/no question returns null on end-of-input"() {
        when:
        def input = ask { it.askYesNoQuestion(TEXT) }

        then:
        1 * userInputReader.readInput() >> UserInputReader.END_OF_INPUT
        0 * userInputHandler._

        and:
        input == null
    }

    def "can ask yes/no question"() {
        when:
        def input = ask { it.askBooleanQuestion(TEXT, true) }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { BooleanQuestionPromptEvent event ->
            assert event.question == 'Accept license?'
            assert event.defaultValue
        }
        1 * userInputReader.readInput() >> new UserInputReader.TextResponse(enteredUserInput)
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == expected

        where:
        enteredUserInput | expected
        'true'           | true
        'false'          | false
    }

    def "yes/no question returns default when empty input line received"() {
        when:
        def input = ask { it.askBooleanQuestion(TEXT, true) }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_ as BooleanQuestionPromptEvent)
        1 * userInputReader.readInput() >> new UserInputReader.TextResponse("")
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == true
    }

    def "yes/no question returns default on end-of-input"() {
        when:
        def input = ask { it.askBooleanQuestion(TEXT, true) }

        then:
        1 * userInputReader.readInput() >> UserInputReader.END_OF_INPUT
        0 * userInputHandler._

        and:
        input == true
    }

    def "can ask select question"() {
        when:
        def input = ask { it.selectOption("select option", [11, 12, 13], 12) }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { SelectOptionPromptEvent event ->
            assert event.question == "select option"
            assert event.options == ["11", "12", "13"]
            assert event.defaultOption == 1
        }
        1 * userInputReader.readInput() >> new UserInputReader.TextResponse("2")
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == 13
    }

    def "can define how to render select options"() {
        when:
        def input = ask {
            it.choice("select option", [11, 12, 13])
                .renderUsing { it + "!" }
                .ask()
        }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { SelectOptionPromptEvent event ->
            assert event.question == "select option"
            assert event.options == ["11!", "12!", "13!"]
            assert event.defaultOption == 0
        }
        1 * userInputReader.readInput() >> new UserInputReader.TextResponse("2")
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == 13
    }

    def "select question does not prompt user when there is only one option"() {
        when:
        def input = ask { it.selectOption(TEXT, [11], 11) }

        then:
        0 * outputEventBroadcaster.onOutput(_)
        0 * userInputHandler._

        and:
        input == 11
    }

    def "select question returns default when empty input line received"() {
        when:
        def input = ask { it.selectOption(TEXT, [11, 12, 13], 12) }

        then:
        1 * userInputReader.readInput() >> new UserInputReader.TextResponse("")
        0 * userInputHandler._

        and:
        input == 12
    }

    def "select question returns default on end-of-input"() {
        when:
        def input = ask { it.selectOption(TEXT, [11, 12, 13], 12) }

        then:
        1 * userInputReader.readInput() >> UserInputReader.END_OF_INPUT
        0 * userInputHandler._

        and:
        input == 12
    }

    def "choice returns first option on end-of-input when no default specified"() {
        when:
        def choice = ask { it.choice(TEXT, [11, 12, 13]) }

        then:
        choiceUsesDefault(choice, 11)
    }

    def "choice returns default option on end-of-input"() {
        when:
        def choice = ask { it.choice(TEXT, [11, 12, 13]).defaultOption(12) }

        then:
        choiceUsesDefault(choice, 12)
    }

    def "choice ignores non-interactive default value"() {
        when:
        def choice = ask { it.choice(TEXT, [11, 12, 13]).whenNotConnected(12) }

        then:
        choiceUsesDefault(choice, 11)
    }

    <T> void choiceUsesDefault(Choice<T> choice, T expected) {
        1 * userInputReader.readInput() >> UserInputReader.END_OF_INPUT
        0 * userInputHandler._

        def input = choice.ask()
        assert input == expected
    }

    def "can ask int question"() {
        when:
        def input = ask { it.askIntQuestion("enter value", 1, 2) }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { IntQuestionPromptEvent event ->
            assert event.question == "enter value"
            assert event.minValue == 1
            assert event.defaultValue == 2
        }
        1 * userInputReader.readInput() >> new UserInputReader.TextResponse("12")
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == 12
    }

    def "uses default value for int question when empty line input received"() {
        when:
        def input = ask { it.askIntQuestion("enter value", 1, 2) }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_ as IntQuestionPromptEvent)
        1 * userInputReader.readInput() >> new UserInputReader.TextResponse("")
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == 2
    }

    def "uses default value for int question when end-of-input received"() {
        when:
        def input = ask { it.askIntQuestion("enter value", 1, 2) }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_ as IntQuestionPromptEvent)
        1 * userInputReader.readInput() >> UserInputReader.END_OF_INPUT
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == 2
    }

    def "can ask text question"() {
        when:
        def input = ask { it.askQuestion("enter value", "value") }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { TextQuestionPromptEvent event ->
            assert event.question == "enter value"
            assert event.defaultValue == "value"
        }
        1 * userInputReader.readInput() >> new UserInputReader.TextResponse("thing")
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == "thing"
    }

    def "text question returns default when empty input line received"() {
        when:
        def input = ask { it.askQuestion(TEXT, "default") }

        then:
        1 * userInputReader.readInput() >> new UserInputReader.TextResponse("")
        0 * userInputHandler._

        and:
        input == "default"
    }

    def "text question returns default on end of input"() {
        when:
        def input = ask { it.askQuestion(TEXT, "default") }

        then:
        1 * userInputReader.readInput() >> UserInputReader.END_OF_INPUT
        0 * userInputHandler._

        and:
        input == "default"
    }

    def "can ask multiple questions in one interaction"() {
        when:
        def input = ask {
            def a = it.askQuestion("enter value", "value")
            def b = it.askQuestion("enter another value", "value")
            [a, b]
        }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { TextQuestionPromptEvent event ->
            assert event.question == "enter value"
        }
        1 * userInputReader.readInput() >> new UserInputReader.TextResponse("thing")
        1 * outputEventBroadcaster.onOutput(_) >> { TextQuestionPromptEvent event ->
            assert event.question == "enter another value"
        }
        1 * userInputReader.readInput() >> new UserInputReader.TextResponse("")
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input == ["thing", "value"]
    }

    def "does not update UI when no question is asked during interaction"() {
        when:
        def input = ask { 12 }

        then:
        input == 12

        and:
        0 * outputEventBroadcaster._
        0 * userInputHandler._
    }

    def "user is prompted lazily when provider value is queried and the result memoized"() {
        def action = Mock(Function)

        when:
        def input = userInputHandler.askUser(action)

        then:
        0 * action._
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        when:
        def result = input.get()

        then:
        1 * action.apply(_) >> { UserQuestions questions -> questions.askQuestion("thing?", "value") }
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_ as TextQuestionPromptEvent)
        1 * userInputReader.readInput() >> new UserInputReader.TextResponse("42")
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)

        and:
        0 * action._
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        result == "42"

        when:
        def result2 = input.get()

        then:
        0 * action._
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        result2 == "42"
    }

    def "memoizes interaction failure"() {
        def action = Mock(Function)
        def failure = new RuntimeException("broken")
        def input = userInputHandler.askUser(action)

        when:
        input.get()

        then:
        def e = thrown(RuntimeException)
        e == failure

        and:
        1 * action.apply(_) >> { throw failure }

        and:
        0 * action._
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        when:
        input.get()

        then:
        def e2 = thrown(RuntimeException)
        e2 == failure

        and:
        0 * action._
        0 * outputEventBroadcaster._
        0 * userInputHandler._
    }

    def "can ask multiple questions in multiple interactions"() {
        when:
        def input1 = ask { it.askQuestion("enter value", "value") }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { TextQuestionPromptEvent event -> assert event.prompt.trim() == "enter value (default: value):" }
        1 * userInputReader.readInput() >> new UserInputReader.TextResponse("thing")
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input1 == "thing"

        when:
        def input2 = ask { it.askQuestion("enter value", "value") }

        then:
        1 * outputEventBroadcaster.onOutput(_ as UserInputRequestEvent)
        1 * outputEventBroadcaster.onOutput(_) >> { TextQuestionPromptEvent event -> assert event.prompt.trim() == "enter value (default: value):" }
        1 * userInputReader.readInput() >> new UserInputReader.TextResponse("")
        1 * outputEventBroadcaster.onOutput(_ as UserInputResumeEvent)
        0 * outputEventBroadcaster._
        0 * userInputHandler._

        and:
        input2 == "value"
    }

    <T> T ask(Closure<T> action) {
        return userInputHandler.askUser(action).getOrNull()
    }

}
