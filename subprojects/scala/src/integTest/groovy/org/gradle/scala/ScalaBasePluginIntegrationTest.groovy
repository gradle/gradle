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
package org.gradle.scala

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ScalaBasePluginIntegrationTest extends AbstractIntegrationSpec {
    def "defaults scalaClasspath to inferred scala compiler dependency if scalaTools configuration is empty"() {
        file("build.gradle") << """
apply plugin: "scala"
repositories {
    mavenCentral()
}
dependencies {
    compile "org.scala-lang:scala-library:2.9.2"
}

task verify << {
    assert compileScala.scalaClasspath.files.any { it.name == "scala-compiler-2.9.2.jar" }
}
"""

        expect:
        succeeds("verify")
    }
}
