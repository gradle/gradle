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

package com.gradle.internal.compiler.java

import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Paths

class AbstractCompilerPluginTest extends Specification {

    @TempDir
    File temporaryFolder

    protected File sourceFolder

    def setup() {
        sourceFolder = Files.createTempDirectory(temporaryFolder.toPath(), null).toFile()
    }

    List<File> toSourceFiles(List<String> bodies) {
        return bodies.collect { toSourceFile(it) }.flatten()
    }

    List<File> toSourceFile(String body) {
        def packageGroup = (body =~ /(?s).*?(?:package) (\w+).*/)
        String packageName = packageGroup.size() > 0 ? packageGroup[0][1] : ""
        def className = (body =~ /(?s).*?(?:class|interface) (\w+).*/)[0][1]
        assert className: "unable to find class name"
        String packageFolder = packageName.replaceAll("[.]", File.separator)
        File parent = Paths.get(sourceFolder.absolutePath, "src", "main", "java", packageFolder).toFile()
        File f = Paths.get(parent.absolutePath, "${className}.java").toFile()
        parent.mkdirs()
        f.text = body
        return [f]
    }

    List<File> toPackageSourceFile(String body) {
        String packageName = (body =~ /(?s).*?(?:package) (\w+).*/)[0][1]
        assert packageName: "unable to find package name"
        def className = "package-info"
        String packageFolder = packageName.replaceAll("[.]", File.separator)
        File parent = Paths.get(sourceFolder.absolutePath, "src", "main", "java", packageFolder).toFile()
        File f = Paths.get(parent.absolutePath, "${className}.java").toFile()
        parent.mkdirs()
        f.text = body
        return [f]
    }

    List<File> toModuleSourceFile(String body) {
        def className = "module-info"
        File parent = Paths.get(sourceFolder.absolutePath, "src", "main", "java").toFile()
        File f = Paths.get(parent.absolutePath, "${className}.java").toFile()
        parent.mkdirs()
        f.text = body
        return [f]
    }

}
