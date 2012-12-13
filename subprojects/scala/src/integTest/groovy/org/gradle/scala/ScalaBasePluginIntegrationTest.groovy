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
