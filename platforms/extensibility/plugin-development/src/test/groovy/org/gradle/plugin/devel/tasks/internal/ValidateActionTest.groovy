/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.plugin.devel.tasks.internal

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ValidateActionTest extends Specification {

    @Rule
    TemporaryFolder temporaryFolder = new TemporaryFolder()

    def "resolves Java source file using package directory structure"() {
        given:
        def root = temporaryFolder.newFolder("src/main/java")
        def pkg = new File(root, "org/example")
        pkg.mkdirs()
        def sourceFile = new File(pkg, "MyTask.java")
        sourceFile.text = "// java file"

        when:
        def resolved = ValidateAction.resolveSourceFile([root], "org.example.MyTask", "MyTask.java")

        then:
        resolved != null
        resolved.canonicalPath == sourceFile.canonicalPath
    }

    def "resolves source file in default root package"() {
        given:
        def root = temporaryFolder.newFolder("src/main/java")
        def sourceFile = new File(root, "RootTask.java")
        sourceFile.text = "// root task"

        when:
        def resolved = ValidateAction.resolveSourceFile([root], "RootTask", "RootTask.java")

        then:
        resolved != null
        resolved.canonicalPath == sourceFile.canonicalPath
    }

    def "resolves Kotlin source file not following package directory layout"() {
        given:
        def root = temporaryFolder.newFolder("src/main/kotlin")
        def otherDir = new File(root, "tasks")
        otherDir.mkdirs()
        def sourceFile = new File(otherDir, "MyKotlinTask.kt")
        sourceFile.text = "package org.example\nclass MyKotlinTask"

        when:
        def resolved = ValidateAction.resolveSourceFile([root], "org.example.MyKotlinTask", "MyKotlinTask.kt")

        then:
        resolved != null
        resolved.canonicalPath == sourceFile.canonicalPath
    }

    def "resolves source file across multiple source roots"() {
        given:
        def javaRoot = temporaryFolder.newFolder("src/main/java")
        def kotlinRoot = temporaryFolder.newFolder("src/main/kotlin")
        def pkg = new File(kotlinRoot, "org/example")
        pkg.mkdirs()
        def sourceFile = new File(pkg, "MyTask.kt")
        sourceFile.text = "// kotlin file"

        when:
        def resolved = ValidateAction.resolveSourceFile([javaRoot, kotlinRoot], "org.example.MyTask", "MyTask.kt")

        then:
        resolved != null
        resolved.canonicalPath == sourceFile.canonicalPath
    }

    def "returns null when duplicate files exist causing ambiguity in fallback search"() {
        given:
        def javaRoot = temporaryFolder.newFolder("src/main/java")
        def kotlinRoot = temporaryFolder.newFolder("src/main/kotlin")
        def file1 = new File(javaRoot, "foo/Duplicate.kt")
        file1.parentFile.mkdirs()
        file1.text = "one"
        def file2 = new File(kotlinRoot, "bar/Duplicate.kt")
        file2.parentFile.mkdirs()
        file2.text = "two"

        when:
        // Declaring class package is "org.example", neither file matches package path
        def resolved = ValidateAction.resolveSourceFile([javaRoot, kotlinRoot], "org.example.Duplicate", "Duplicate.kt")

        then:
        resolved == null
    }

    def "returns null when duplicate files exist in package lookup"() {
        given:
        def root1 = temporaryFolder.newFolder("root1")
        def root2 = temporaryFolder.newFolder("root2")
        def file1 = new File(root1, "org/example/MyTask.java")
        file1.parentFile.mkdirs()
        file1.text = "one"
        def file2 = new File(root2, "org/example/MyTask.java")
        file2.parentFile.mkdirs()
        file2.text = "two"

        when:
        def resolved = ValidateAction.resolveSourceFile([root1, root2], "org.example.MyTask", "MyTask.java")

        then:
        resolved == null
    }

    def "returns null when source file does not exist"() {
        given:
        def root = temporaryFolder.newFolder("src/main/java")

        when:
        def resolved = ValidateAction.resolveSourceFile([root], "org.example.NonExistent", "NonExistent.java")

        then:
        resolved == null
    }

    def "returns null when source roots list is empty"() {
        expect:
        ValidateAction.resolveSourceFile([], "org.example.MyTask", "MyTask.java") == null
    }
}
