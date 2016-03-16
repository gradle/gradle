/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.language.gosu.fixtures

import org.gradle.integtests.fixtures.jvm.JvmSourceFile
import org.gradle.integtests.fixtures.jvm.TestJvmComponent
import org.gradle.language.gosu.GosuLanguageSourceSet

class TestGosuComponent extends TestJvmComponent {

    String languageName = 'gosu'
    String sourceSetTypeName = GosuLanguageSourceSet.class.name

    List<JvmSourceFile> sources = [
        new JvmSourceFile('compile/test', 'Person.gs', '''
package compile.test

class Person {

  var _name : String
  var _age : Integer

  construct(name: String, age: Integer) {
    _name = name
    _age = age
  }

  override function toString() : String {
    return _name + ", " + _age
  }
}'''),
        new JvmSourceFile('compile/test', 'Person2.gs', '''
package compile.test

class Person2 {
}
''')
    ]

    /**
     * The Gosu parser reads from both source and class files at runtime
     * Thus the Gosu compiler places sources alongside classes in the target dir
     *
     * @return sources, class files and resources
     */
    @Override
    List<JvmSourceFile> getExpectedClasses(){
        return getSources().collect{it}.plus(getSources().collect{it.classFile});
    }

    @Override
    List<String> getSourceFileExtensions() {
        return ['gs', 'gsx', 'gst', 'gsp']
    }
}
