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

package org.gradle.integtests.resolve.attributes

import org.gradle.integtests.fixtures.GradleMetadataResolveRunner
import org.gradle.integtests.fixtures.RequiredFeature
import org.gradle.integtests.fixtures.RequiredFeatures
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import org.gradle.test.fixtures.file.TestFile
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode;

class ClasspathDependenciesAttributesIntegrationTest extends AbstractModuleDependencyResolveTest {

    def 'buildscript classpath resolves java-runtime variant'() {
        def otherSettings = file('other/settings.gradle')
        def otherBuild = file('other/build.gradle')

        otherSettings << """
rootProject.name = 'other'
"""
        otherBuild << """
group = 'org.other'
version = '1.0'
configurations {
    conf {
        canBeConsumed = true
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
        }
    }
    badConf {
        canBeConsumed = true
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, 'not-good'))
        }
    }
}
"""

        buildFile << """
buildscript {
    dependencies {
        classpath 'org.other:other:1.0'
    }
}
"""

        expect:
        executer.withArguments('--include-build', 'other')
        succeeds()
    }

    @RequiredFeatures([
        @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "false"),
        @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    ])
    def 'show that settings classpath respects attributes and thus will use the default java-runtime value'() {
        given:
        def jarFile = file('build/lib/foo.jar')
        createJarFile(jarFile)
        def foo = mavenRepo.module('org', 'foo')
        foo.publish()
        foo.artifact.file.bytes = jarFile.bytes
        mavenRepo.module('org', 'bar').dependsOn(['scope': 'runtime'], foo).publish()

        settingsFile << """
import org.gradle.api.internal.model.NamedObjectInstantiator
buildscript {
    $repositoryDeclaration

    configurations.classpath {
        attributes.attribute(Usage.USAGE_ATTRIBUTE, NamedObjectInstantiator.INSTANCE.named(Usage, Usage.JAVA_API))
    }
    
    dependencies {
        classpath 'org:bar:1.0'
    }
}    
    Class.forName('org.gradle.MyClass')
"""

        repositoryInteractions {
            'org:bar:1.0' {
                expectResolve()
            }
        }
        when:
        fails()

        then:
        failure.assertHasCause("org.gradle.MyClass")
    }

    private void createJarFile(TestFile jar) {
        TestFile contents = file('contents')
        TestFile classFile = contents.createFile('org/gradle/MyClass.class')

        ClassNode classNode = new ClassNode()
        classNode.version = Opcodes.V1_6
        classNode.access = Opcodes.ACC_PUBLIC
        classNode.name = 'org/gradle/MyClass'
        classNode.superName = 'java/lang/Object'

        ClassWriter cw = new ClassWriter(0)
        classNode.accept(cw)

        classFile.withDataOutputStream {
            it.write(cw.toByteArray())
        }

        contents.zipTo(jar)
    }

}
