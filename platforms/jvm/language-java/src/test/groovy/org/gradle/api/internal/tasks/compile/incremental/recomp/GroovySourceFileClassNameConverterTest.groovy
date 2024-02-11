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
import com.google.common.collect.Multimaps
import spock.lang.Specification
import spock.lang.Subject

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
        sourceClassesMapping.put('YourOtherClass.groovy', 'org.gradle.YourClass')

        converter = new DefaultSourceFileClassNameConverter(Multimaps.asMap(sourceClassesMapping))
    }

    def 'can get class names by file'() {
        expect:
        converter.getClassNames(file) == classes

        where:
        file                | classes
        'MyClass.groovy'    | ['org.gradle.MyClass1', 'org.gradle.MyClass2'] as Set
        'YourClass.groovy'  | ['org.gradle.YourClass'] as Set
        'OtherClass.groovy' | [] as Set
    }

    def 'can get files by classname'() {
        expect:
        converter.getRelativeSourcePaths(fqcn) == files

        where:
        fqcn                    | files
        'org.gradle.MyClass1'   | ['MyClass.groovy'] as Set
        'org.gradle.MyClass2'   | ['MyClass.groovy'] as Set
        'org.gradle.YourClass'  | ['YourClass.groovy', 'YourOtherClass.groovy'] as Set
        'org.gradle.OtherClass' | [] as Set
    }
}
