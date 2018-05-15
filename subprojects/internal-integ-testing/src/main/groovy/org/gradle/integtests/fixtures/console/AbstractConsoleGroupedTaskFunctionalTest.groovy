/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.integtests.fixtures.console

import org.gradle.api.logging.configuration.ConsoleOutput
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.RichConsoleStyling
import org.gradle.integtests.fixtures.executer.ConsoleAttachment
import org.junit.Assume
import org.junit.runner.RunWith

/**
 * A base class for testing the console.
 */
@RunWith(ConsoleAttachmentTestRunner.class)
abstract class AbstractConsoleGroupedTaskFunctionalTest extends AbstractIntegrationSpec implements RichConsoleStyling {
    static ConsoleAttachment consoleAttachment

    def setup() {
        // TODO - rich/verbose consoles currently exhibit slightly different behavior than these tests expect when no console is attached
        Assume.assumeFalse(consoleAttachment == ConsoleAttachment.NOT_ATTACHED && (consoleType == ConsoleOutput.Rich || consoleType == ConsoleOutput.Verbose))

        switch(consoleAttachment) {
            case ConsoleAttachment.NOT_ATTACHED:
                break
            case ConsoleAttachment.ATTACHED:
                executer.withTestConsoleAttached()
                break
            case ConsoleAttachment.ATTACHED_STDOUT_ONLY:
                executer.withTestConsoleAttachedToStdoutOnly()
                break
            default:
                throw new IllegalArgumentException()
        }

        executer.beforeExecute {
            it.withConsole(consoleType)
        }
    }

    abstract ConsoleOutput getConsoleType()
}
