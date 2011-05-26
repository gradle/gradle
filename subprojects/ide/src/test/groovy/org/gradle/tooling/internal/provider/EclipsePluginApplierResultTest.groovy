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

package org.gradle.tooling.internal.provider

import spock.lang.Specification

/**
 * @author: Szczepan Faber, created at: 5/26/11
 */
class EclipsePluginApplierResultTest extends Specification {

    def result = new EclipsePluginApplierResult()

    def "remembers applied tasks"() {
        when:
        result.rememberTasks(":", ['rootTask'])
        result.rememberTasks(":foo", ['cool', 'tasks'])
        result.rememberTasks(":foo:bar", [])

        then:
        result.wasApplied(":rootTask")
        result.wasApplied(":foo:cool")
        result.wasApplied(":foo:tasks")

        !result.wasApplied(":")
        !result.wasApplied(":root")
        !result.wasApplied(":foo")
        !result.wasApplied(":foo:bar")
        !result.wasApplied(":foo:bar:baz")
    }
}