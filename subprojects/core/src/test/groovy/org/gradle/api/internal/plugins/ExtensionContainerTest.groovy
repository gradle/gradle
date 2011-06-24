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

package org.gradle.api.internal.plugins;


import org.gradle.api.GradleException
import org.gradle.api.plugins.ExtensionContainer
import spock.lang.Specification

/**
 * @author: Szczepan Faber, created at: 6/24/11
 */
public class ExtensionContainerTest extends Specification {

    def ExtensionContainer container = new DefaultConvention()
    def extension = new FooExtension()

    class FooExtension {
        String message = "smile"
    }

    def "extension can be accessed and configured"() {
        when:
        container.add("foo", extension)
        container.foo.message = "Hey!"

        then:
        extension.message == "Hey!"
    }

    def "extension can be configured via script block"() {
        when:
        container.add("foo", extension)
        container.foo {
            message = "You cool?"
        }

        then:
        extension.message == "You cool?"
    }

    def "extension cannot be set as property because we want users to use explicit method to add extensions"() {
        when:
        container.add("foo", extension)
        container.foo = new FooExtension()

        then:
        thrown(GradleException)
    }
}