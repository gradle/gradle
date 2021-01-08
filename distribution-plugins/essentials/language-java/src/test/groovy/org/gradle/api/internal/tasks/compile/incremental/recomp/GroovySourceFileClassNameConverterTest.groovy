/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.recomp

import com.google.common.collect.Multimap
import com.google.common.collect.MultimapBuilder
import spock.lang.Specification
import spock.lang.Subject
import spock.lang.Unroll

class GroovySourceFileClassNameConverterTest extends Specification {
    @Subject
    DefaultSourceFileClassNameConverter converter

    def setup() {
        Multimap<String, String> sourceClassesMapping = MultimapBuilder.SetMultimapBuilder
            .hashKeys()
            .hashSetValues()
            .build()

        sourceClassesMapping.put('MyClass.groovy', 'org.gradle.MyClass1')
        sourceClassesMapping.put('MyClass.groovy', 'org.gradle.MyClass2')
        sourceClassesMapping.put('YourClass.groovy', 'org.gradle.YourClass')

        converter = new DefaultSourceFileClassNameConverter(sourceClassesMapping)
    }

    @Unroll
    def 'can get class names by file'() {
        expect:
        converter.getClassNames(file) == classes

        where:
        file                | classes
        'MyClass.groovy'    | ['org.gradle.MyClass1', 'org.gradle.MyClass2'] as Set
        'YourClass.groovy'  | ['org.gradle.YourClass'] as Set
        'OtherClass.groovy' | [] as Set
    }

    @Unroll
    def 'can get file by classname'() {
        expect:
        converter.getRelativeSourcePath(fqcn) == file
        where:
        fqcn                    | file
        'org.gradle.MyClass1'   | Optional.of('MyClass.groovy')
        'org.gradle.MyClass2'   | Optional.of('MyClass.groovy')
        'org.gradle.YourClass'  | Optional.of('YourClass.groovy')
        'org.gradle.OtherClass' | Optional.empty()
    }
}
