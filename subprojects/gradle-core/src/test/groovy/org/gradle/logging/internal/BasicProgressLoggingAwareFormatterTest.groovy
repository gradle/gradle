/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.logging.internal

import org.gradle.api.logging.LogLevel
import org.gradle.api.logging.StandardOutputListener

class BasicProgressLoggingAwareFormatterTest extends OutputSpecification {
    private final StandardOutputListener infoMessage = Mock()
    private final StandardOutputListener errorMessage = Mock()
    private final BasicProgressLoggingAwareFormatter formatter = new BasicProgressLoggingAwareFormatter(infoMessage, errorMessage)

    public void logsEventWithMessage() {
        when:
        formatter.onOutput(event('message'))

        then:
        1 * infoMessage.onOutput(toNative('message\n'))
        0 * infoMessage._
        0 * errorMessage._
    }

    public void logsEventWithMessageAndException() {
        when:
        formatter.onOutput(event('message', new RuntimeException('broken')))

        then:
        1 * infoMessage.onOutput({it.startsWith(toNative('message\n')) && it.contains('java.lang.RuntimeException: broken')})
        0 * infoMessage._
        0 * errorMessage._
    }

    public void logsDebugEventWithMessage() {
        when:
        formatter.onOutput(new LogLevelChangeEvent(LogLevel.DEBUG))
        formatter.onOutput(event(getTime('10:12:01.905'), 'message'))

        then:
        1 *infoMessage.onOutput(toNative('10:12:01.905 [INFO] [category] message\n'))
        0 * infoMessage._
        0 * errorMessage._
    }

    public void logsEventWithErrorMessage() {
        when:
        formatter.onOutput(event('message', LogLevel.ERROR))

        then:
        1 * errorMessage.onOutput(toNative('message\n'))
        0 * infoMessage._
        0 * errorMessage._
    }

    public void logsProgressMessages() {
        when:
        formatter.onOutput(start('description'))
        formatter.onOutput(complete('complete'))

        then:
        1 * infoMessage.onOutput('description')
        1 * infoMessage.onOutput(' ')
        1 * infoMessage.onOutput('complete')
        1 * infoMessage.onOutput(toNative('\n'))
        0 * infoMessage._
        0 * errorMessage._
    }

    public void ignoresProgressStatusMessages() {
        when:
        formatter.onOutput(start('description'))
        formatter.onOutput(progress('tick'))
        formatter.onOutput(progress('tick'))
        formatter.onOutput(complete('complete'))

        then:
        1 * infoMessage.onOutput('description')
        1 * infoMessage.onOutput(' ')
        1 * infoMessage.onOutput('complete')
        1 * infoMessage.onOutput(toNative('\n'))
        0 * infoMessage._
        0 * errorMessage._
    }

    public void logsNestedProgressMessages() {
        when:
        formatter.onOutput(start('description1'))
        formatter.onOutput(start('description2'))
        formatter.onOutput(progress('tick'))
        formatter.onOutput(progress('tick'))
        formatter.onOutput(complete('complete2'))
        formatter.onOutput(complete('complete1'))

        then:
        1 * infoMessage.onOutput('description1')
        3 * infoMessage.onOutput(toNative('\n'))
        1 * infoMessage.onOutput('description2')
        1 * infoMessage.onOutput(' ')
        1 * infoMessage.onOutput('complete2')
        1 * infoMessage.onOutput('complete1')
        0 * infoMessage._
        0 * errorMessage._
    }

    public void logsMixOfProgressAndInfoMessages() {
        when:
        formatter.onOutput(start('description'))
        formatter.onOutput(progress('tick'))
        formatter.onOutput(event('message'))
        formatter.onOutput(progress('tick'))
        formatter.onOutput(complete('complete'))

        then:
        1 * infoMessage.onOutput('description')
        2 * infoMessage.onOutput(toNative('\n'))
        1 * infoMessage.onOutput(String.format('message%n'))
        1 * infoMessage.onOutput('complete')
        0 * infoMessage._
        0 * errorMessage._
    }

    public void logsMixOfProgressAndErrorMessages() {
        when:
        formatter.onOutput(start('description'))
        formatter.onOutput(event('message', LogLevel.ERROR))
        formatter.onOutput(complete('complete'))

        then:
        1 * infoMessage.onOutput('description')
        2 * infoMessage.onOutput(toNative('\n'))
        1 * errorMessage.onOutput(toNative('message\n'))
        1 * infoMessage.onOutput('complete')
        0 * infoMessage._
        0 * errorMessage._
    }

    public void logsProgressMessagesWithNoCompletionStatus() {
        when:
        formatter.onOutput(start('description'))
        formatter.onOutput(complete(''))

        then:
        1 * infoMessage.onOutput('description')
        1 * infoMessage.onOutput(toNative('\n'))
        0 * infoMessage._
        0 * errorMessage._
    }

    public void logsProgressMessagesWithNoCompletionStatusAndOtherMessages() {
        when:
        formatter.onOutput(start('description'))
        formatter.onOutput(event('message'))
        formatter.onOutput(complete(''))

        then:
        1 * infoMessage.onOutput('description')
        1 * infoMessage.onOutput(toNative('\n'))
        1 * infoMessage.onOutput(toNative('message\n'))
        0 * infoMessage._
        0 * errorMessage._
    }

    public void logsProgressMessagesWithNoStartStatus() {
        when:
        formatter.onOutput(start(''))

        then:
        0 * infoMessage._
        0 * errorMessage._

        when:
        formatter.onOutput(complete('done'))

        then:
        1 * infoMessage.onOutput('done')
        1 * infoMessage.onOutput(toNative('\n'))
        0 * infoMessage._
        0 * errorMessage._
    }

    public void logsNestedProgressMessagesWithNoStartStatusAndOtherMessages() {
        when:
        formatter.onOutput(start('outer'))

        then:
        1 * infoMessage.onOutput('outer')
        0 * infoMessage._
        0 * errorMessage._

        when:
        formatter.onOutput(start(''))

        then:
        0 * infoMessage._
        0 * errorMessage._

        when:
        formatter.onOutput(event('message', LogLevel.ERROR))

        then:
        1 * infoMessage.onOutput(toNative('\n'))
        1 * errorMessage.onOutput(toNative('message\n'))

        when:
        formatter.onOutput(complete('done inner'))

        then:
        1 * infoMessage.onOutput('done inner')
        1 * infoMessage.onOutput(toNative('\n'))

        when:
        formatter.onOutput(complete('done outer'))

        then:
        1 * infoMessage.onOutput('done outer')
        1 * infoMessage.onOutput(toNative('\n'))
    }
}
