/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.profile

import org.gradle.api.tasks.TaskState
import spock.lang.Specification

class ProjectProfileTest extends Specification {

    def "provides sorted tasks"() {
        def profile = new ProjectProfile(":foo")
        def a = profile.getTaskProfile("foo:a").completed(Stub(TaskState)).setStart(100).setFinish(300)
        def b = profile.getTaskProfile("foo:b").completed(Stub(TaskState)).setStart(300).setFinish(300)
        def c = profile.getTaskProfile("foo:c").completed(Stub(TaskState)).setStart(300).setFinish(300)
        def d = profile.getTaskProfile("foo:d").completed(Stub(TaskState)).setStart(301).setFinish(302)

        expect:
        profile.tasks.operations == [a, d, b, c]
    }
}
