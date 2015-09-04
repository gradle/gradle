/*
 * Copyright 2014 the original author or authors.
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



package org.gradle.language.fixtures

import org.gradle.integtests.fixtures.jvm.JvmSourceFile

class BadJavaComponent extends TestJavaComponent {
    List<JvmSourceFile> sources = [
        new JvmSourceFile("compile/test", "Person.java", '''
package compile.test;

import java.util.Arrays;

public class Person {
    String name;
    int age;

    void hello() {
        return nothing
    }
}'''),
        new JvmSourceFile("compile/test", "Person2.java", '''
Not a java source file at all...
''')
    ]

    List<String> compilerErrors = [
            "';' expected",
            "class, interface, or enum expected"
    ]
}
