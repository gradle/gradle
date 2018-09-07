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

package org.gradle.api.internal

import org.gradle.api.Action
import spock.lang.Subject

@Subject(ThreadLocalMutationGuard)
class ThreadLocalMutationGuardTest extends AbstractMutationGuardSpec {
    final MutationGuard guard = new ThreadLocalMutationGuard()

    def "doesn't protect across thread boundaries"() {
        given:
        def callable = AbstractMutationGuardSpec.ActionCallingDisallowedMethod.newInstance(this)
        def action = guard.withMutationDisabled(new Action<Void>() {
            @Override
            void execute(Void aVoid) {
                def thread = new Thread(new Runnable() {
                    @Override
                    void run() {
                        callable.execute(aVoid)
                    }
                })
                thread.start()
                thread.join()
            }
        })

        when:
        action.execute(null)

        then:
        noExceptionThrown()
        callable.noExceptionThrown()
        callable.called
    }
}
