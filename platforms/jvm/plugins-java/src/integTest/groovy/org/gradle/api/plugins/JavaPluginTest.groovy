/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.api.JavaVersion
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.javadoc.Javadoc
import org.gradle.api.tasks.testing.Test
import org.gradle.internal.jvm.Jvm
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.test.fixtures.AbstractProjectBuilderSpec

class JavaPluginTest extends AbstractProjectBuilderSpec {
    def "wires toolchain for sourceset if toolchain is configured"() {
        given:
        def someJdk = Jvm.current()
        setupProjectWithToolchain(someJdk.javaVersion)

        when:
        project.sourceSets.create('custom')

        then:
        def compileTask = project.tasks.named("compileCustomJava", JavaCompile).get()
        def configuredToolchain = compileTask.javaCompiler.get().javaToolchain
        configuredToolchain.displayName.contains(someJdk.javaVersion.getMajorVersion())
    }

    def "source and target compatibility are configured if toolchain is configured"() {
        given:
        setupProjectWithToolchain(Jvm.current().javaVersion)

        when:
        project.sourceSets.create('custom')

        then:
        JavaVersion.toVersion(project.tasks.compileJava.getSourceCompatibility()).majorVersion == Jvm.current().javaVersion.majorVersion
        JavaVersion.toVersion(project.tasks.compileJava.getTargetCompatibility()).majorVersion == Jvm.current().javaVersion.majorVersion
        JavaVersion.toVersion(project.tasks.compileCustomJava.getSourceCompatibility()).majorVersion == Jvm.current().javaVersion.majorVersion
        JavaVersion.toVersion(project.tasks.compileCustomJava.getTargetCompatibility()).majorVersion == Jvm.current().javaVersion.majorVersion
    }

    def "wires toolchain for test if toolchain is configured"() {
        given:
        def someJdk = Jvm.current()
        setupProjectWithToolchain(someJdk.javaVersion)

        when:
        def testTask = project.tasks.named("test", Test).get()
        def configuredJavaLauncher = testTask.javaLauncher.get()

        then:
        configuredJavaLauncher.executablePath.toString().contains(someJdk.javaVersion.getMajorVersion())
    }

    def "wires toolchain for javadoc if toolchain is configured"() {
        given:
        def someJdk = Jvm.current()
        setupProjectWithToolchain(someJdk.javaVersion)

        when:
        def javadocTask = project.tasks.named("javadoc", Javadoc).get()
        def configuredJavadocTool = javadocTask.javadocTool

        then:
        configuredJavadocTool.isPresent()
    }

    def 'can set java compile source compatibility if toolchain is configured'() {
        given:
        def someJdk = Jvm.current()
        setupProjectWithToolchain(someJdk.javaVersion)
        def prevJavaVersion = JavaVersion.toVersion(someJdk.javaVersion.majorVersion.toInteger() - 1)
        project.java.sourceCompatibility = prevJavaVersion

        when:
        def javaCompileTask = project.tasks.named("compileJava", JavaCompile).get()

        then:
        javaCompileTask.sourceCompatibility == prevJavaVersion.toString()
        javaCompileTask.sourceCompatibility == prevJavaVersion.toString()
    }

    private void setupProjectWithToolchain(JavaVersion version) {
        project.pluginManager.apply(JavaPlugin)
        project.java.toolchain.languageVersion = JavaLanguageVersion.of(version.majorVersion)
    }
}
