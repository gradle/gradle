/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.java.compile.incremental


import org.gradle.integtests.fixtures.CompiledLanguage
import org.gradle.integtests.fixtures.DirectoryBuildCacheFixture
import spock.lang.Issue

@Issue("https://github.com/gradle/gradle/issues/4951")
class JavaIncrementalCompilationWithCachingIntegrationTest extends AbstractJavaGroovyIncrementalCompilationSupport implements DirectoryBuildCacheFixture {

    @Override
    CompiledLanguage getLanguage() {
        return CompiledLanguage.JAVA
    }

    @Override
    def setup() {
        buildFile << """
            ${mavenCentralRepository()}
        """
    }

    def "incremental state is restored from the build cache"() {
        source("class A {}", "class B {}")
        withBuildCache().run language.compileTaskName
        def cachedBuild = outputs.snapshot {
            withBuildCache().run "clean", language.compileTaskName
        }
        cachedBuild.groupedOutput.task(":compileJava").outcome == "FROM_CACHE"

        when:
        source("class A { /* ${UUID.randomUUID()} */ }")
        withBuildCache().run language.compileTaskName, "--info"

        then:
        outputs.recompiledClasses("A")
    }

    def "classpath analysis is restored from the build cache"() {
        requireOwnGradleUserHomeDir().requireDaemon().requireIsolatedDaemons()
        buildFile << "dependencies { implementation 'org.apache.commons:commons-lang3:3.8' }\n"
        source("class A {}")
        withBuildCache().run language.compileTaskName
        executer.stop()
        file("user-home/caches").deleteDir()
        def cachedBuild =  withBuildCache().run "clean", language.compileTaskName
        cachedBuild.groupedOutput.task(":compileJava").outcome == "FROM_CACHE"

        when:
        buildFile << "dependencies { implementation 'org.apache.commons:commons-lang3:3.9' }\n"
        def incrementalBuild = withBuildCache().run language.compileTaskName, "--info"
        def incrementalCompile = incrementalBuild.groupedOutput.task(":compileJava")

        then:
        incrementalCompile.outcome == "UP-TO-DATE"
        incrementalCompile.output.contains("None of the classes needs to be compiled")
    }
}
