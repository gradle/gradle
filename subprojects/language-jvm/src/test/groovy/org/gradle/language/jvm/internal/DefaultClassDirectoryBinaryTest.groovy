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
        binary.namingScheme.getTaskName(null, null) == 'main'
        binary.namingScheme.getTaskName("compile", null) == 'compileMain'
        binary.namingScheme.getTaskName(null, "groovy") == 'groovy'
        binary.namingScheme.getTaskName("compile", "groovy") == 'compileGroovy'
    }

    def "uses medium task names for binary with name 'otherClasses'"() {
        when:
        def binary = new DefaultClassDirectoryBinary("otherClasses")

        then:
        binary.name == 'otherClasses'

        and:
        binary.namingScheme.getTaskName(null, null) == 'other'
        binary.namingScheme.getTaskName("compile", null) == 'compileOther'
        binary.namingScheme.getTaskName(null, "groovy") == 'otherGroovy'
        binary.namingScheme.getTaskName("compile", "groovy") == 'compileOtherGroovy'
    }

    def "uses full task names for binary with name 'otherBinary'"() {
        when:
        def binary = new DefaultClassDirectoryBinary("otherBinary")

        then:
        binary.name == 'otherBinary'

        and:
        binary.namingScheme.getTaskName(null, null) == 'otherBinary'
        binary.namingScheme.getTaskName("compile", null) == 'compileOtherBinary'
        binary.namingScheme.getTaskName(null, "groovy") == 'otherBinaryGroovy'
        binary.namingScheme.getTaskName("compile", "groovy") == 'compileOtherBinaryGroovy'
    }

    def "has a useful toString() representation"() {
        expect:
        def binary = new DefaultClassDirectoryBinary(name)
        binary.toString() == displayName

        where:
        name           | displayName
        'mainClasses'  | 'classes \'main\''
        'otherClasses' | 'classes \'other\''
        'custom'       | 'classes \'custom\''
    }
}
