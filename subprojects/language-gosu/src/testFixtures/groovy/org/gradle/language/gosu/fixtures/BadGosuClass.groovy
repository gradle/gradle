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

class BadGosuClass {
    List<JvmSourceFile> sources = [
        new JvmSourceFile('compile/test', 'Person.gs', '''
package compile.test

class Person {
  construct() {
    var x : int = "Intentional error"
  }
}'''),
        new JvmSourceFile('compile/test', 'Person2.gs', '''
package compile.test

class Person2 {
  function makeAnError() {
    failIntentionally() //this method does not exist
  }
}
''')
    ]

    List<String> compilerErrors = [
        'compile/test/Person.gs:[6,19] error: The type "java.lang.String" cannot be converted to "int"',
        'compile/test/Person2.gs:[6,5] error: No function defined for failIntentionally.'

    ]
}
