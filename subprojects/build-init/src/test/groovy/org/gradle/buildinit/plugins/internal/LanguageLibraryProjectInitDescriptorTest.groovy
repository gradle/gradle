/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.buildinit.plugins.internal

import org.gradle.api.file.FileTree
import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.FileResolver
import spock.lang.Specification

class LanguageLibraryProjectInitDescriptorTest extends Specification {

    FileResolver fileresolver = Mock(FileResolver)

    LanguageLibraryProjectInitDescriptor descriptor
    TemplateBasedFileGenerator fileGenerator = Mock(TemplateBasedFileGenerator)

    def setup(){
        descriptor = new LanguageLibraryProjectInitDescriptor("somepackage", "somelang", Mock(TemplateLibraryVersionProvider), fileresolver, Mock(DocumentationRegistry))
        descriptor.fileGenerator = fileGenerator
    }

    def "skipsClassGenerationIfSourcesExist"() {
        setup:
        when:
        withSources()
        descriptor.generateClass("src/main", "SomeClass.java")
        then:
        0 * fileresolver.resolve(_)
        0 * fileGenerator.generate(_, _, _)
    }

    def "generatesSourcesIfSourcesNotExistYet"() {
        when:
        withNoSources()
        descriptor.generateClass("src/main", "SomeClass.java")
        then:
        1 * fileresolver.resolve(_) >> new File("someFile")
        1 * fileGenerator.generate(_, _, _)
    }

    def withNoSources() {
        resolve("src/main/somelang", true)
        resolve("src/test/somelang", true)
    }

    def withSources() {
        resolve("src/main/somelang", false)
        resolve("src/test/somelang", false)
    }

    def resolve(String path, empty) {
        _ * fileresolver.resolveFilesAsTree(path) >> {
            def tree = Mock(FileTree)
            _ * tree.empty >> empty
            return tree
        }
    }
}
