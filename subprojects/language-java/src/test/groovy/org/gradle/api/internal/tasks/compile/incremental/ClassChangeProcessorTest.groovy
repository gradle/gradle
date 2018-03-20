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
package org.gradle.api.internal.tasks.compile.incremental

import org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet
import org.gradle.api.internal.tasks.compile.incremental.jar.PreviousCompilation
import org.gradle.api.internal.tasks.compile.incremental.recomp.RecompilationSpec
import org.gradle.api.tasks.incremental.InputFileDetails
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import spock.lang.Specification
import spock.lang.Subject

import static org.spockframework.util.CollectionUtil.asSet

class ClassChangeProcessorTest extends Specification {
    @Rule TestNameTestDirectoryProvider tempDir = new TestNameTestDirectoryProvider()
    def previousCompilation = Mock(PreviousCompilation)
    def dependentsSet = Mock(DependentsSet)

    def inputFileDetails = Stub(InputFileDetails) {
         getFile() >> { createClassFile('org/gradle/MyClass') }
    }

    @Subject classChangeProcessor = new ClassChangeProcessor(previousCompilation)

    def "adds dependents to given recompilation spec"() {
        given:
        def recompilationSpec = new RecompilationSpec()
        1 * previousCompilation.getDependents(_ as String, _ as Set) >> { dependentsSet }
        1 * dependentsSet.getDependentClasses() >> { asSet('org/gradle/MainClass', 'org/gradle/OtherClass') }

        when:
        classChangeProcessor.processChange(inputFileDetails, recompilationSpec)

        then:
        recompilationSpec.getClassesToCompile() == asSet('org/gradle/OtherClass', 'org/gradle/MainClass')
    }

    def "marks full rebuild given a class that is dependency to all"() {
        given:
        1 * dependentsSet.isDependencyToAll() >> { true }
        1 * dependentsSet.getDescription() >> { "DEPENDENTS_DESCRIPTION" }
        1 * previousCompilation.getDependents(_ as String, _ as Set) >> { dependentsSet }
        def recompilationSpec = new RecompilationSpec()

        when:
        classChangeProcessor.processChange(inputFileDetails, recompilationSpec)

        then:
        recompilationSpec.getFullRebuildCause()
    }

    def "throws IllegalArgumentException given unreadable .class file"() {
        given:
        def bogusInput = Stub(InputFileDetails) {
            getFile() >> { tempDir.file("BOGUS.notevenaclass") }
        }

        when:
        classChangeProcessor.processChange(bogusInput, new RecompilationSpec())

        then:
        thrown IllegalArgumentException
    }

    private TestFile createClassFile(String className) {
        TestFile contents = tempDir.createDir('build/classes')
        TestFile classFile = contents.createFile("${className}.class")

        ClassNode classNode = new ClassNode()
        classNode.version = Opcodes.V1_6
        classNode.access = Opcodes.ACC_PUBLIC
        classNode.name = className
        classNode.superName = 'java/lang/Object'

        ClassWriter cw = new ClassWriter(0)
        classNode.accept(cw)

        classFile.withDataOutputStream {
            it.write(cw.toByteArray())
        }
        classFile
    }
}
