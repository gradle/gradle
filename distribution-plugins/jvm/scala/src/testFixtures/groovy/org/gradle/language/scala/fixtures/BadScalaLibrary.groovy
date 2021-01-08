/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.language.scala.fixtures

import org.gradle.integtests.fixtures.jvm.JvmSourceFile

class BadScalaLibrary {
    List<JvmSourceFile> sources = [
            new JvmSourceFile("compile/test", "Person.scala", '''
package compile.test;

class Person(name: String, age: Integer) {
    def toString(): String = name + ", " + age;
}'''),
            new JvmSourceFile("compile/test", "Person2.scala", '''
package compile.test;

class Person2 {
    def test;
}
''')
    ]

    List<String> compilerErrors = [
            "Person.scala:5: overriding method toString in class Object of type ()String",
            "Person2.scala:4: class Person2 needs to be abstract, since method test is not defined"

    ]
}
