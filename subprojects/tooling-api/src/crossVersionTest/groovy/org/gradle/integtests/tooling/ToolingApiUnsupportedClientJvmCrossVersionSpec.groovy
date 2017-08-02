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
import org.gradle.util.Requires

class ToolingApiUnsupportedClientJvmCrossVersionSpec extends ToolingApiSpecification {
    def setup() {
        settingsFile << "rootProject.name = 'test'"

        buildFile << """
apply plugin: 'application'
sourceCompatibility = 1.5
targetCompatibility = 1.5
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
import org.gradle.tooling.ProjectConnection;
import java.io.File;
import java.net.URI;

public class TestClient {
    public static void main(String[] args) {
        try {
            ProjectConnection connection = GradleConnector
                .newConnector()
                .forProjectDirectory(new File(new URI("${projectDir.toURI()}")))
                .useInstallation(new File(new URI("${buildContext.gradleHomeDir.toURI()}")))
                .connect();
            connection.newBuild().run();
        } catch(Throwable t) {
            t.printStackTrace(System.out);
        }
        System.exit(0);
    }
}
"""
        targetDist.executer(temporaryFolder, getBuildContext()).inDirectory(projectDir).withTasks("installDist").requireGradleDistribution().run()
    }

    @Requires(adhoc = { AvailableJavaHomes.getJdks("1.5", "1.6") })
    @TargetGradleVersion("current")
    @ToolingApiVersion("current")
    def "cannot use tooling API from Java 6 or earlier"() {
        when:
        def out = runScript(jdk)

        then:
        out.contains("Gradle Tooling API ${targetDist.version.version} requires Java 7 or later to run. You are currently using Java ${jdk.javaVersion.majorVersion}.")

        where:
        jdk << AvailableJavaHomes.getJdks("1.5", "1.6")
    }

    def runScript(def jdk) {
        def outStr = new ByteArrayOutputStream()
        def executer = new ScriptExecuter()
        executer.environment(JAVA_HOME: jdk.javaHome)
        executer.workingDir(projectDir)
        executer.standardOutput = outStr
        executer.commandLine("build/install/test/bin/test")
        executer.run()
        println outStr
        return outStr.toString()
    }
}
