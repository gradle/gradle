/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.scala.compile

import org.gradle.integtests.fixtures.MultiVersionIntegrationSpec
import org.gradle.integtests.fixtures.TargetCoverage
import org.gradle.integtests.fixtures.ZincCoverage

@TargetCoverage({ZincCoverage.ALL_VERSIONS})
class ZincScalaCompilerMultiVersionIntegrationTest extends MultiVersionIntegrationSpec {
    def setup() {
        buildFile << """
            apply plugin: "scala"

            ${jcenterRepository()}

            dependencies {
                compile "org.scala-lang:scala-library:2.10.7"
                zinc "com.typesafe.zinc:zinc:0.3.0"
            }
        """
        args("--info")
    }

    def "can build with configured zinc compiler version" () {
        given:
        withScalaSources()

        expect:
        succeeds("compileScala")
        output.contains("Compiling with Zinc Scala compiler")
        scalaClassFile("compile/test").assertContainsDescendants(
            "Person.class",
            "Person2.class"
        )
    }

    def withScalaSources() {
        file("src/main/scala/compile/test/Person.scala") <<
            """
package compile.test

import scala.collection.JavaConversions._

class Person(val name: String, val age: Int) {
    def hello() {
        val x: java.util.List[Int] = List(3, 1, 2)
        java.util.Collections.reverse(x)
    }
}
"""
        file("src/main/scala/compile/test/Person2.scala") <<
            """
package compile.test

class Person2(name: String, age: Int) extends Person(name, age) {
}
"""
    }
}
