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

package org.gradle.language.jvm.internal.plugins
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.bundling.Zip
import org.gradle.language.base.BinaryContainer
import org.gradle.language.base.internal.BinaryNamingScheme
import org.gradle.language.jvm.JvmLibraryBinary
import org.gradle.language.jvm.internal.JvmLibraryBinaryInternal
import spock.lang.Specification

import static org.gradle.util.WrapUtil.toNamedDomainObjectSet

class CreateTasksForJvmBinariesTest extends Specification {
    def rule = new CreateTasksForJvmBinaries()
    def tasks = Mock(TaskContainer)
    def binaries = Mock(BinaryContainer)

    def "creates a 'jar' tasks for each jvm library binary"() {
        def jvmLibraryBinary = Mock(JvmLibraryBinaryInternal)
        def namingScheme = Mock(BinaryNamingScheme)
        def jarTask = Mock(Zip)

        when:
        1 * binaries.withType(JvmLibraryBinaryInternal) >> toNamedDomainObjectSet(JvmLibraryBinary, jvmLibraryBinary)

        and:
        rule.createTasks(tasks, binaries)

        then:
        _ * jvmLibraryBinary.name >> "binaryName"
        _ * jvmLibraryBinary.displayName >> "binaryDisplayName"
        1 * jvmLibraryBinary.namingScheme >> namingScheme
        1 * namingScheme.getTaskName("create") >> "theTaskName"
        1 * tasks.create("theTaskName", Zip) >> jarTask
        1 * jarTask.setDescription("Creates the binary file for binaryDisplayName.")
        1 * jvmLibraryBinary.builtBy(jarTask)
        0 * _
    }

    def "does nothing for non-jvm binaries"() {
        when:
        1 * binaries.withType(JvmLibraryBinaryInternal) >> toNamedDomainObjectSet(JvmLibraryBinary)

        and:
        rule.createTasks(tasks, binaries)

        then:
        0 * _
    }
}
