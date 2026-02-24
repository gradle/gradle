/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.jvm.toolchain

import org.gradle.api.JavaVersion
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.AvailableJavaHomes
import org.gradle.integtests.fixtures.jvm.JavaToolchainFixture
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassVisitor
import org.objectweb.asm.ModuleVisitor
import org.objectweb.asm.Opcodes

class JavaToolchainVersionSelectionIntegrationTest extends AbstractIntegrationSpec implements JavaToolchainFixture {

    def "implementation version is considered for toolchain selection"() {
        given:
        def jdks = AvailableJavaHomes.getAvailableJdks(JavaVersion.VERSION_21).toSorted {
            it.implementationJavaVersion
        }
        withInstallations(jdks)
        // If multiple JDKs are available pick the one with the lowest version.
        def selectedVersion = jdks.first().implementationJavaVersion

        buildFile """
            plugins { id 'java' }
            java.toolchain { implementationVersion = '${selectedVersion}' }
        """
        javaFile 'src/main/java/module-info.java', 'module org.example {}'

        when:
        run 'compileJava'

        then:
        assertCompiledWith file('build/classes/java/main/module-info.class'), selectedVersion
    }

    /**
     * A module-info.class always has a 'requires java.base' with the implementation version of the JDK that was used for compilation.
     */
    private static void assertCompiledWith(File clazz, String implementationVersion) {
        ClassVisitor cv = new ClassVisitor(Opcodes.ASM9) {
            @Override
            ModuleVisitor visitModule(String n, int a, String v) {
                ModuleVisitor moduleVisitor = super.visitModule(n, a, v)
                return new ModuleVisitor(Opcodes.ASM9, moduleVisitor) {
                    @Override
                    void visitRequire(String module, int access, String version) {
                        super.visitRequire(module, access, version)
                        assert module == 'java.base'
                        assert version == implementationVersion
                    }
                }
            }
        }
        ClassReader reader = new ClassReader(clazz.bytes)
        reader.accept(cv, 0)
    }
}
