/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.file

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.Unroll

class FileCollectionIntegrationTest extends AbstractIntegrationSpec {
    @Unroll
    def "can use 'as' operator with #type"() {
        buildFile << """
            def fileCollection = files("input.txt")
            def castValue = fileCollection as $type
            println "Cast value: \$castValue (\${castValue.getClass().name})"
            assert castValue instanceof $type
        """

        expect:
        succeeds "help"

        where:
        type << ["Object", "Object[]", "Set", "LinkedHashSet", "List", "LinkedList", "Collection", "FileCollection"]
    }

    def "using 'as' operator with File type produces deprecation warning"() {
        buildFile << """
            def fileCollection = files("input.txt")
            def castValue = fileCollection as File
            assert castValue instanceof File
            assert castValue.name == "input.txt"
        """

        executer.expectDeprecationWarning().withFullDeprecationStackTraceDisabled()

        expect:
        succeeds "help"
        output.contains "Do not cast FileCollection to File. This has been deprecated and is scheduled to be removed in Gradle 5.0. Call getSingleFile() instead."
    }

    def "using 'as' operator with File[] type produces deprecation warning"() {
        buildFile << """
            def fileCollection = files("input.txt")
            def castValue = fileCollection as File[]
            assert castValue instanceof File[]
            assert castValue*.name == ["input.txt"]
        """

        executer.expectDeprecationWarning().withFullDeprecationStackTraceDisabled()

        expect:
        succeeds "help"
        output.contains "Do not cast FileCollection to File[]. This has been deprecated and is scheduled to be removed in Gradle 5.0"
    }

    def "using 'as' operator with FileTree type produces deprecation warning"() {
        file("input.txt").createFile()
        buildFile << """
            def fileCollection = files("input.txt")
            def castValue = fileCollection as FileTree
            assert castValue instanceof FileTree
            castValue.visit { entry ->
                println "- \$entry"
            }
        """

        executer.expectDeprecationWarning().withFullDeprecationStackTraceDisabled()

        expect:
        succeeds "help"
        output.contains "input.txt"
        output.contains "Do not cast FileCollection to FileTree. This has been deprecated and is scheduled to be removed in Gradle 5.0. Call getAsFileTree() instead."
    }

    def "using 'FileCollection.add()' produces deprecation warning"() {
        file("input.txt").createFile()
        buildFile << """
            files().plus(files()).add(files())
        """

        executer.expectDeprecationWarning().withFullDeprecationStackTraceDisabled()

        expect:
        succeeds "help"
        output.contains "The FileCollection.add() method has been deprecated. This is scheduled to be removed in Gradle 5.0. Please use the ConfigurableFileCollection.from() method instead."
    }

    def "using 'FileTree.add()' produces deprecation warning"() {
        file("input.txt").createFile()
        buildFile << """
            files().asFileTree.plus(files().asFileTree).add(files().asFileTree)
        """

        executer.expectDeprecationWarning().withFullDeprecationStackTraceDisabled()

        expect:
        succeeds "help"
        output.contains "The FileCollection.add() method has been deprecated. This is scheduled to be removed in Gradle 5.0. Please use the ConfigurableFileTree.from() method instead."
    }

    def "getting build dependencies from custom file collection produces deprecation warning"() {
        buildFile << """
            class CustomFileCollection extends org.gradle.api.internal.file.AbstractFileCollection {
                String displayName = "custom"
                Set<File> files = []
            }

            new CustomFileCollection().buildDependencies
        """

        executer.expectDeprecationWarning().withFullDeprecationStackTraceDisabled()

        expect:
        succeeds "help"
        output.contains "The AbstractFileCollection.getBuildDependencies() method has been deprecated. This is scheduled to be removed in Gradle 5.0. CustomFileCollection extends AbstractFileCollection. Do not extend AbstractFileCollection. Use Project.files() instead."
    }
}
