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

package org.gradle.language.jvm.internal;

import spock.lang.Specification;

public class DefaultClassDirectoryBinaryTest extends Specification {
    def "uses short task names for binary with name 'mainClasses'"() {
        when:
        def binary = new DefaultClassDirectoryBinary("mainClasses")

        then:
        binary.name == 'mainClasses'

        and:
        binary.getTaskName(null, null) == 'main'
        binary.getTaskName("compile", null) == 'compileMain'
        binary.getTaskName(null, "groovy") == 'groovy'
        binary.getTaskName("compile", "groovy") == 'compileGroovy'
    }

    def "uses medium task names for binary with name 'otherClasses'"() {
        when:
        def binary = new DefaultClassDirectoryBinary("otherClasses")

        then:
        binary.name == 'otherClasses'

        and:
        binary.getTaskName(null, null) == 'other'
        binary.getTaskName("compile", null) == 'compileOther'
        binary.getTaskName(null, "groovy") == 'otherGroovy'
        binary.getTaskName("compile", "groovy") == 'compileOtherGroovy'
    }

    def "uses full task names for binary with name 'otherBinary'"() {
        when:
        def binary = new DefaultClassDirectoryBinary("otherBinary")

        then:
        binary.name == 'otherBinary'

        and:
        binary.getTaskName(null, null) == 'otherBinary'
        binary.getTaskName("compile", null) == 'compileOtherBinary'
        binary.getTaskName(null, "groovy") == 'otherBinaryGroovy'
        binary.getTaskName("compile", "groovy") == 'compileOtherBinaryGroovy'
    }
}
