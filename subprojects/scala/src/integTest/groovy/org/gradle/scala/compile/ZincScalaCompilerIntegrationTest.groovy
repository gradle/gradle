/*
 * Copyright 2012 the original author or authors.
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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition

@Requires(TestPrecondition.JDK5)
class ZincScalaCompilerIntegrationTest extends AbstractIntegrationSpec {
    def "gives sensible error when run with Java 5"() {
        buildFile <<
"""
apply plugin: "scala"

repositories {
    mavenCentral()
}

dependencies {
    scalaTools "org.scala-lang:scala-compiler:2.9.2"
    compile "org.scala-lang:scala-library:2.9.2"
}
"""

        file("src/main/scala/Person.scala") << "class Person"

        expect:
        fails("compileScala")
        failure.output.contains("To run the Zinc Scala compiler, Java 6 or higher is required.")
    }
}