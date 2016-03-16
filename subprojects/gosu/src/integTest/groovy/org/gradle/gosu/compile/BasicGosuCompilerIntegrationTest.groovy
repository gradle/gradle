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

package org.gradle.gosu.compile

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec

abstract class BasicGosuCompilerIntegrationTest extends MultiVersionIntegrationSpec {
    def setup() {
        args("-i", "-PgosuVersion=$version")
        buildFile << buildScript()
        buildFile <<
            """
DeprecationLogger.whileDisabled {
    ${compilerConfiguration()}
}
"""
    }

    def compileGoodCode() {
        given:
        goodCode()

        expect:
        succeeds('compileGosu')
        output.contains(logStatement())
        file('build/classes/main/compile/test/Person.gs').exists()
        file('build/classes/main/compile/test/Person.class').exists()
    }

    def compileBadCode() {
        given:
        badCode()

        expect:
        fails('compileGosu')
        output.contains(logStatement())
//        errorOutput.contains('You must explicitly coerce int to java.lang.String using the as keyword') //FIXME stdErr not showing up?
        errorOutput.contains(':compileGosu completed with 1 error')
        file('build/classes/main').assertHasDescendants()
    }

    def compileBadCodeWithoutFailing() {
        given:
        badCode()

        and:
        buildFile <<
            '''
compileGosu.gosuCompileOptions.failOnError = false
'''

        expect:
        succeeds('compileGosu')
        output.contains(logStatement())
//        errorOutput.contains('You must explicitly coerce int to java.lang.String using the as keyword') //FIXME stdErr not showing up?
        errorOutput.contains(':compileGosu completed with 1 error')
        file('build/classes/main').assertHasDescendants()
    }

    def buildScript() {
        """
apply plugin: 'gosu'

repositories {
    mavenCentral()
}

dependencies {
    compile "org.gosu-lang.gosu:gosu-core-api:$version"
}
"""
    }

    abstract String compilerConfiguration()

    abstract String logStatement()

    def goodCode() {
        file('src/main/gosu/compile/test/Person.gs') <<
            """
package compile.test

class Person {

  var _name: String
  var _age: int

  construct(name: String, age: int) {
    _name = name
    _age = age
  }

  function hello() {
    var x = { 3, 1, 2 }
    Collections.reverse(x)
  }
}
"""
        file('src/main/gosu/compile/test/Person2.gs') <<
            """
package compile.test

class Person2 extends Person {
}
"""
    }

    def badCode() {
        file('src/main/gosu/compile/test/Person.gs') <<
            """
package compile.test

class Person {

  property get hello() : String {
    return 42
  }
}
"""
    }

}

