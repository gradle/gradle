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

package org.gradle.runtime.jvm.internal.plugins
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.bundling.Jar
import org.gradle.runtime.base.BinaryContainer
import org.gradle.runtime.base.internal.BinaryNamingScheme
import org.gradle.runtime.jvm.JarBinary
import org.gradle.runtime.jvm.JvmBinaryTasks
import org.gradle.runtime.jvm.internal.ProjectJvmBinaryInternal
import spock.lang.Specification

import static org.gradle.util.WrapUtil.toNamedDomainObjectSet

class CreateTasksForJarBinariesTest extends Specification {
    def rule = new CreateTasksForJarBinaries()
    def tasks = Mock(TaskContainer)
    def binaries = Mock(BinaryContainer)

    def "creates a 'jar' tasks for each jar library binary"() {
        def jvmLibraryBinary = Mock(JarBinaryInternal)
        def namingScheme = Mock(BinaryNamingScheme)
        def jarTask = Mock(Jar)
        def binaryTasks = Mock(JvmBinaryTasks)
        def classesDir = Mock(File)
        def jarFile = Mock(File)

        when:
        1 * binaries.withType(JarBinary) >> toNamedDomainObjectSet(JarBinary, jvmLibraryBinary)

        and:
        rule.createTasks(tasks, binaries)

        then:
        _ * jvmLibraryBinary.name >> "binaryName"
        2 * jvmLibraryBinary.namingScheme >> namingScheme
        1 * namingScheme.description >> "binaryDisplayName"
        1 * jvmLibraryBinary.classesDir >> classesDir
        2 * jvmLibraryBinary.jarFile >> jarFile
        1 * jarFile.parentFile >> jarFile
        1 * jarFile.name >> "binary.jar"
        1 * namingScheme.getTaskName("create") >> "theTaskName"

        1 * tasks.create("theTaskName", Jar) >> jarTask
        1 * jarTask.setDescription("Creates the binary file for binaryDisplayName.")
        1 * jarTask.from(classesDir)
        1 * jarTask.setDestinationDir(jarFile)
        1 * jarTask.setArchiveName("binary.jar")

        1 * jvmLibraryBinary.getTasks() >> binaryTasks
        1 * binaryTasks.add(jarTask)
        1 * jvmLibraryBinary.builtBy(jarTask)
        0 * _
    }

    def "does nothing for non-jvm binaries"() {
        when:
        1 * binaries.withType(JarBinary) >> toNamedDomainObjectSet(JarBinary)

        and:
        rule.createTasks(tasks, binaries)

        then:
        0 * _
    }

    interface JarBinaryInternal extends JarBinary, ProjectJvmBinaryInternal {}
}
