/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.compile


import spock.lang.Specification

public class BaseForkOptionsTest extends Specification {
    def 'JVM options are filtered properly even with bad input'() {
	    def options = new BaseForkOptions()

        options.jvmArgs = ['', '', '']

        expect:
        options.jvmArgs.isEmpty()
    }

    def 'JVM options are preserved if they are not bad'() {
        def options = new BaseForkOptions()

        options.jvmArgs = ['x', '', 'y']

        expect:
        options.jvmArgs.size() == 2
        options.jvmArgs[0] == 'x'
        options.jvmArgs[1] == 'y'
    }
}
