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
import org.gradle.integtests.fixtures.jvm.TestJvmComponent

class TestJointCompiledComponent extends TestJvmComponent {

    String languageName = "scala"

    List<JvmSourceFile> sources = [
            new JvmSourceFile("compile/test", "Person.scala", '''
package compile.test;

class Person(name: String, age: Integer) {
    override def toString(): String = name + ", " + age;
}'''),
            new JvmSourceFile("compile/test", "Person2.java", '''
package compile.test;

class Person2 {
    String name;
    String age;
}
''')
    ]

    @Override
    List<String> getSourceFileExtensions() {
        return ["java", "scala"]
    }

}
