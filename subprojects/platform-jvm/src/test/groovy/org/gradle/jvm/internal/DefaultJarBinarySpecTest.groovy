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

package org.gradle.jvm.internal

import org.gradle.api.internal.project.taskfactory.ITaskFactory
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.jvm.JarBinarySpec
import org.gradle.jvm.JvmLibrarySpec
import org.gradle.jvm.platform.JavaPlatform
import org.gradle.jvm.toolchain.JavaToolChain
import org.gradle.platform.base.binary.BaseBinarySpec
import spock.lang.Specification

class DefaultJarBinarySpecTest extends Specification {
    def library = Mock(JvmLibrarySpec)
    def toolChain = Mock(JavaToolChain)
    def platform = Mock(JavaPlatform)

    def "binary takes name and displayName from naming scheme"() {
        when:
        def binary = binary()

        then:
        binary.name == "jvm-lib-jar"
        binary.displayName == "Jar 'jvm-lib-jar'"
    }

    def "binary has properties for classesDir and jar file"() {
        when:
        def binary = binary()

        then:
        binary.jarFile == null
        binary.classesDir == null

        when:
        def jarFile = Mock(File)
        def classesDir = Mock(File)

        and:
        binary.jarFile = jarFile
        binary.classesDir = classesDir

        then:
        binary.jarFile == jarFile
        binary.classesDir == classesDir
    }

    private DefaultJarBinarySpec binary() {
        BaseBinarySpec.create(JarBinarySpec, DefaultJarBinarySpec, "jvm-lib-jar", DirectInstantiator.INSTANCE, Mock(ITaskFactory))
    }
}
