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


package org.gradle.integtests.tooling

import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.ScriptExecuter
import org.gradle.integtests.tooling.fixture.TargetGradleVersion
import org.gradle.integtests.tooling.fixture.ToolingApiSpecification
import org.gradle.integtests.tooling.fixture.ToolingApiVersion
import org.gradle.util.GradleVersion
import spock.lang.IgnoreIf

@IgnoreIf({ AvailableJavaHomes.jdk6 == null })
class ToolingApiDeprecatedClientJvmCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        settingsFile << "rootProject.name = 'test'"

        buildFile << """
apply plugin: 'application'
sourceCompatibility = 1.6
targetCompatibility = 1.6
repositories {
    maven {
        url 'https://repo.gradle.org/gradle/libs-releases-local'
    }
    maven {
        url '${buildContext.libsRepo.toURI()}'
    }
    mavenCentral()
}

dependencies {
    compile "org.gradle:gradle-tooling-api:${GradleVersion.current().version}"
    runtime 'org.slf4j:slf4j-simple:1.7.10'
}

mainClassName = 'TestClient'
"""
        file('src/main/java/TestClient.java') << """
import org.gradle.tooling.GradleConnector;

public class TestClient {
    public static void main(String[] args) {
        GradleConnector.newConnector();
        System.exit(0);
    }
}
"""
        targetDist.executer(temporaryFolder).inDirectory(projectDir).withTasks("installDist").requireGradleHome().run()
    }

    @TargetGradleVersion("current")
    @ToolingApiVersion("current")
    def "warning when using tooling API from Java 6"() {
        when:
        def out = runScript()

        then:
        out.contains("Support for using the Gradle Tooling API with Java 6 is deprecated and will be removed in Gradle 3.0")
    }

    String runScript() {
        def outStr = new ByteArrayOutputStream()
        def executer = new ScriptExecuter()
        executer.environment(JAVA_HOME: AvailableJavaHomes.jdk6.javaHome)
        executer.workingDir(projectDir)
        executer.errorOutput = outStr // simple slf4j writes warnings to stderr
        executer.commandLine("build/install/test/bin/test")
        executer.run().assertNormalExitValue()
        println outStr
        return outStr.toString()
    }
}
