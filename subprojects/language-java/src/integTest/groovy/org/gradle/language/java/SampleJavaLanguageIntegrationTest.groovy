/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.language.java

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.Sample
import org.gradle.test.fixtures.archive.JarTestFixture
import org.junit.Rule

class SampleJavaLanguageIntegrationTest extends AbstractIntegrationSpec {

    @Rule
    Sample quickstart = new Sample(temporaryFolder, "javaLibraryPlugin/quickstart")

    @Rule
    Sample platformAware = new Sample(temporaryFolder, "javaLibraryPlugin/targetplatforms")

    @Rule
    Sample multicomponent = new Sample(temporaryFolder, "javaLibraryPlugin/multiplecomponents")

    @Rule
    Sample apiSpec = new Sample(temporaryFolder, "javaLibraryPlugin/apispec")

    @Rule
    Sample apiSpecSupport = new Sample(temporaryFolder, "javaLibraryPlugin/apispec-support")

    def "quickstart sample builds java based jvm component"() {
        setup:
        executer.inDirectory(quickstart.dir)

        when:
        succeeds("assemble")

        then:
        new JarTestFixture(quickstart.dir.file("build/jars/main/jar/main.jar")).hasDescendants(
                "org/gradle/Person.class", "org/gradle/resource.xml"
        )
    }

    def "targetplatforms sample creates a binary specific source set"() {
        setup:
        executer.inDirectory(platformAware.dir)

        when:
        succeeds("assemble")

        then: "the Java 5 version of the jar doesn't include any Java 6 class"
        new JarTestFixture(platformAware.dir.file("core/build/jars/main/java5Jar/main.jar")).hasDescendants(
            "org/gradle/Person.class", "org/gradle/resource.xml"
        )

        and: "the Java 6 jar contains the Person6 class"
        new JarTestFixture(platformAware.dir.file("core/build/jars/main/java6Jar/main.jar")).hasDescendants(
            "org/gradle/Person.class", "org/gradle/Person6.class", "org/gradle/resource.xml"
        )

        and:
        new JarTestFixture(platformAware.dir.file("server/build/jars/main/java6Jar/main.jar")).hasDescendants(
            "org/gradle/Server.class"
        )
    }

    def "multiplecomponents sample builds multiple jars"() {
        setup:
        executer.inDirectory(multicomponent.dir)

        when:
        succeeds("assemble")

        then:
        new JarTestFixture(multicomponent.dir.file("build/jars/client/jar/client.jar")).hasDescendants(
            "org/gradle/Client.class"
        )

        and:
        new JarTestFixture(multicomponent.dir.file("build/jars/server/jar/server.jar")).hasDescendants(
            "org/gradle/PersonServer.class"
        )

        and:
        new JarTestFixture(multicomponent.dir.file("build/jars/core/jar/core.jar")).hasDescendants(
            "org/gradle/Person.class", "org/gradle/resource.xml"
        )

        and:
        new JarTestFixture(multicomponent.dir.file("util/build/jars/main/jar/main.jar")).hasDescendants(
            "org/gradle/Utils.class"
        )
    }

    def "demonstrates compile avoidance by declaring an API"() {
        setup:
        executer.inDirectory(apiSpec.dir)

        when:
        succeeds ':mainJar', ':clientJar'

        then:
        new JarTestFixture(apiSpec.dir.file("build/jars/main/jar/main.jar")).hasDescendants(
            "org/gradle/Person.class",
            "org/gradle/utils/StringUtils.class",
            "org/gradle/internal/PersonInternal.class",
            "org/gradle/resource.xml"
        )

        new JarTestFixture(apiSpec.dir.file("build/jars/main/jar/api/main.jar")).hasDescendants(
            "org/gradle/Person.class",
            "org/gradle/utils/StringUtils.class"
        )

        and:
        new JarTestFixture(apiSpec.dir.file("build/jars/client/jar/client.jar")).hasDescendants(
            "org/gradle/Client.class"
        )

        when:
        executer.inDirectory(apiSpec.dir)

        then:
        fails ':brokenclientJar'

        when:
        executer.inDirectory(apiSpecSupport.dir)
        succeeds ':updateMainComponent'
        executer.inDirectory(apiSpec.dir)

        then:
        succeeds ':clientJar'
        executedAndNotSkipped ':mainApiJar'
        skipped ':compileClientJarClientJava'
    }
}
