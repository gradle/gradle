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



package org.gradle.api.internal.tasks.compile.incremental

import org.gradle.api.internal.tasks.compile.incremental.recomp.CompilationSourceDirs
import org.gradle.api.internal.tasks.compile.incremental.recomp.JavaSourceToNameConverter
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification
import spock.lang.Subject

class SourceToNameConverterTest extends Specification {

    @Rule TestNameTestDirectoryProvider temp = new TestNameTestDirectoryProvider()
    def srcDirs = Stub(CompilationSourceDirs) {
        getSourceRoots() >> ["src/main/java", "src/main/java2"].collect { temp.file(it) }
    }
    @Subject converter = new JavaSourceToNameConverter(srcDirs)

    def "knows java source class relative path"() {
        expect:
        converter.getClassNames(temp.file("src/main/java/Foo.java")) == "Foo"
        converter.getClassNames(temp.file("src/main/java/org/bar/Bar.java")) == "org.bar.Bar"
        converter.getClassNames(temp.file("src/main/java2/com/Com.java")) == "com.Com"

        when: converter.getClassNames(temp.file("src/main/unknown/Xxx.java"))
        then: thrown(IllegalArgumentException)
    }
}
