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
import org.gradle.integtests.resolve.AbstractModuleDependencyResolveTest
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.plugin.PluginBuilder
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode

class ClasspathDependenciesAttributesIntegrationTest extends AbstractModuleDependencyResolveTest {

    def pluginBuilder = new PluginBuilder(file('plugin'))

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "false")
    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    def 'module metadata fetched through a settings useModule properly derives variants and subsequent project use of the dependency has access to derived variants'() {
        given:
        def module = mavenRepo.module('test', 'dep', '1.0').publish()
        mavenRepo.module('test', 'bom', '1.0').hasPackaging('pom').dependencyConstraint(module).publish()

        pluginBuilder.addPlugin("println 'test-plugin applied'")
        def pluginModule = pluginBuilder.publishAs('test:plugin:1.0', mavenRepo, executer).pluginModule
        def pomFile = pluginModule.pom.file
        // Adds a dependency on the BOM to show that variant derivation is then available during project use
        pomFile.text = pomFile.text.replace('</project>', """
  <dependencies>
    <dependency>
      <groupId>test</groupId>
      <artifactId>bom</artifactId>
      <version>1.0</version>
    </dependency>
  </dependencies>

</project>
""")

        settingsFile.text = """
pluginManagement {
    repositories {
        maven { url = '$mavenRepo.uri'}
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == 'test-plugin') {
                useModule('test:plugin:1.0')
            }
        }
    }
}
$settingsFile.text
"""
        buildFile.text = """
plugins {
    id 'java'
    id 'test-plugin'
}

repositories {
    maven { url = '$mavenRepo.uri'}
}

dependencies {
    implementation platform('test:bom:1.0')
    implementation 'test:dep'
}

task printDeps {
    def compileClasspath = configurations.compileClasspath
    doLast {
        println compileClasspath.files
    }
}
"""

        when:
        succeeds 'printDeps'

        then:
        outputContains 'test-plugin applied'
    }


    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "true")
    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    def 'module metadata fetched through a settings useModule properly uses Java ecosystem'() {
        given:

        // Create module that will match only if compatibility and disambiguation rules are in place
        mavenRepo.module('test', 'dep', '1.0')
            .variant("runtime", ["org.gradle.usage": "java-runtime", 'org.gradle.dependency.bundling': 'embedded'])
            .variant("conflictingRuntime", ["org.gradle.usage": "java-runtime", 'org.gradle.dependency.bundling': 'shadowed'])
            .withModuleMetadata()
            .publish()

        pluginBuilder.addPlugin("println 'test-plugin applied'")
        def pluginModule = pluginBuilder.publishAs('test:plugin:1.0', mavenRepo, executer).pluginModule
        def pomFile = pluginModule.pom.file
        // Adds a dependency on the BOM to show that variant derivation is then available during project use
        pomFile.text = pomFile.text.replace('</project>', """
  <dependencies>
    <dependency>
      <groupId>test</groupId>
      <artifactId>dep</artifactId>
      <version>1.0</version>
    </dependency>
  </dependencies>

</project>
""")

        settingsFile.text = """
pluginManagement {
    repositories {
        maven { url = '$mavenRepo.uri' }
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == 'test-plugin') {
                useModule('test:plugin:1.0')
            }
        }
    }
}
$settingsFile.text
"""
        buildFile.text = """
plugins {
    id 'java'
    id 'test-plugin'
}

repositories {
    maven { url = '$mavenRepo.uri' }
}
"""


        when:
        succeeds 'help'

        then:
        outputContains 'test-plugin applied'
    }

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
        assert canBeConsumed
        attributes {
            attribute(Usage.USAGE_ATTRIBUTE, objects.named(Usage, Usage.JAVA_RUNTIME))
        }
    }
    badConf {
        assert canBeConsumed
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

    @RequiredFeature(feature = GradleMetadataResolveRunner.GRADLE_METADATA, value = "false")
    @RequiredFeature(feature = GradleMetadataResolveRunner.REPOSITORY_TYPE, value = "maven")
    def 'show that settings classpath respects attributes and thus will use the default java-runtime value'() {
        given:
        def jarFile = file('build/lib/foo.jar')
        createJarFile(jarFile)
        def foo = mavenRepo.module('org', 'foo')
        foo.publish()
        foo.artifact.file.bytes = jarFile.bytes
        mavenRepo.module('org', 'bar').dependsOn(['scope': 'runtime'], foo).publish()

        settingsFile << """
buildscript {
    $repositoryDeclaration

    configurations.classpath {
        attributes.attribute(Usage.USAGE_ATTRIBUTE, services.get(ObjectFactory).named(Usage, Usage.JAVA_API))
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
