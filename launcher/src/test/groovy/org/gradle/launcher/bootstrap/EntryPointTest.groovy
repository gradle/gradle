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

package org.gradle.launcher.bootstrap

import spock.lang.Specification
import org.gradle.api.Action

class EntryPointTest extends Specification {
    final Action<ExecutionListener> action = Mock()
    final ProcessCompleter completer = Mock()
    final EntryPoint entryPoint = new EntryPoint() {
        @Override
        protected ExecutionCompleter createCompleter() {
            return completer
        }

        @Override
        protected void doAction(String[] args, ExecutionListener listener) {
            action.execute(listener)
        }
    }

    def "exits with success when action executes successfully"() {
        when:
        entryPoint.run()

        then:
        1 * action.execute(!null)
        1 * completer.complete()
        0 * _._
    }

    def "exits with failure when action reports a failure"() {
        def failure = new RuntimeException()

        when:
        entryPoint.run()

        then:
        1 * action.execute(!null) >> { ExecutionListener listener -> listener.onFailure(failure) }
        1 * completer.completeWithFailure(failure)
        0 * _._
    }

    def "exits with failure when action throws exception"() {
        def failure = new RuntimeException()

        when:
        entryPoint.run()

        then:
        1 * action.execute(!null) >> { throw failure }
        1 * completer.completeWithFailure(failure)
        0 * _._
    }
}
