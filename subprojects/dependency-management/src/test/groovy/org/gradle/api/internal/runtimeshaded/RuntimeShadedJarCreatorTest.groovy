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

package org.gradle.api.internal.runtimeshaded

import groovy.transform.stc.ClosureParams
import groovy.transform.stc.SimpleType
import org.apache.ivy.core.settings.IvySettings
import org.gradle.api.Action
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.IoActions
import org.gradle.internal.classpath.ClasspathBuilder
import org.gradle.internal.classpath.ClasspathWalker
import org.gradle.internal.installation.GradleRuntimeShadedJarDetector
import org.gradle.internal.logging.progress.ProgressLoggerFactory
import org.gradle.test.fixtures.file.CleanupTestDirectory
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.UsesNativeServices
import org.junit.Rule
import org.objectweb.asm.ClassReader
import org.objectweb.asm.ClassWriter
import org.objectweb.asm.Opcodes
import org.objectweb.asm.tree.ClassNode
import org.objectweb.asm.util.TraceClassVisitor
import spock.lang.Issue
import spock.lang.Specification

import java.util.jar.JarEntry
import java.util.jar.JarFile

@UsesNativeServices
@CleanupTestDirectory(fieldName = "tmpDir")
class RuntimeShadedJarCreatorTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def progressLoggerFactory = Stub(ProgressLoggerFactory)
    def relocatedJarCreator
    def outputJar = tmpDir.testDirectory.file('gradle-api.jar')

    def setup() {
        relocatedJarCreator = new RuntimeShadedJarCreator(
            progressLoggerFactory,
            new ImplementationDependencyRelocator(RuntimeShadedJarType.API),
            new ClasspathWalker(TestFiles.fileSystem()),
            new ClasspathBuilder(TestFiles.tmpDirTemporaryFileProvider(tmpDir.createDir("tmp")))
        )
    }

    def "creates JAR file for input directory"() {
        given:
        def inputFilesDir = tmpDir.createDir('inputFiles')
        writeClass(inputFilesDir, "org/gradle/MyClass")

        when:
        relocatedJarCreator.create(outputJar, [inputFilesDir])

        then:
        TestFile[] contents = tmpDir.testDirectory.listFiles().findAll { it.isFile() }
        contents.length == 1
        contents[0] == outputJar
    }

    def "creates fat JAR file for multiple input JAR files"() {
        given:
        def className = 'org/gradle/MyClass'
        def inputFilesDir = tmpDir.createDir('inputFiles')
        def jarFile1 = inputFilesDir.file('lib1.jar')
        createJarFileWithClassFiles(jarFile1, [className])
        def jarFile2 = inputFilesDir.file('lib2.jar')
        createJarFileWithClassFiles(jarFile2, [className])

        when:
        relocatedJarCreator.create(outputJar, [jarFile1, jarFile2])

        then:
        TestFile[] contents = tmpDir.testDirectory.listFiles().findAll { it.isFile() }
        contents.length == 1
        contents[0] == outputJar
    }

    def "creates a reproducible jar"() {
        given:

        def inputFilesDir = tmpDir.createDir('inputFiles')
        def jarFile1 = inputFilesDir.file('lib1.jar')
        createJarFileWithClassFiles(jarFile1, ['org/gradle/MyClass'])
        def jarFile2 = inputFilesDir.file('lib2.jar')
        createJarFileWithClassFiles(jarFile2, ['org/gradle/MySecondClass'])
        def jarFile3 = inputFilesDir.file('lib3.jar')
        def serviceType = 'org.gradle.internal.service.scopes.PluginServiceRegistry'
        createJarFileWithProviderConfigurationFile(jarFile3, serviceType, 'org.gradle.api.internal.artifacts.DependencyServices')
        def jarFile4 = inputFilesDir.file('lib4.jar')
        createJarFileWithProviderConfigurationFile(jarFile4, serviceType, """
# This is some same file
# Ignore comment
org.gradle.api.internal.tasks.CompileServices
# Too many comments""")
        def jarFile5 = inputFilesDir.file('lib5.jar')
        createJarFileWithResources(jarFile5, [
            'org/gradle/reporting/report.js',
            'net/rubygrapefruit/platform/osx-i386/libnative-platform.dylib',
            'org/joda/time/tz/data/Africa/Abidjan'])
        def jarFile6 = inputFilesDir.file('lib6.jar')
        createJarFileWithProviderConfigurationFile(jarFile6, 'org.gradle.internal.other.Service', 'org.gradle.internal.other.ServiceImpl')
        def inputDirectory = inputFilesDir.createDir('dir1')
        writeClass(inputDirectory, "org/gradle/MyFirstClass")
        writeClass(inputDirectory, "org/gradle/MyAClass")
        writeClass(inputDirectory, "org/gradle/MyBClass")

        when:
        relocatedJarCreator.create(outputJar, [jarFile1, jarFile2, jarFile3, jarFile4, jarFile5, jarFile6, inputDirectory])

        then:

        TestFile[] contents = tmpDir.testDirectory.listFiles().findAll { it.isFile() }
        contents.length == 1
        contents[0] == outputJar
        handleAsJarFile(outputJar) { JarFile file ->
            List<JarEntry> entries = file.entries() as List
            assert entries*.name == [
                'org/',
                'org/gradle/',
                'org/gradle/MyClass.class',
                'org/gradle/MySecondClass.class',
                'net/',
                'net/rubygrapefruit/',
                'net/rubygrapefruit/platform/',
                'net/rubygrapefruit/platform/osx-i386/',
                'net/rubygrapefruit/platform/osx-i386/libnative-platform.dylib',
                'org/gradle/reporting/',
                'org/gradle/reporting/report.js',
                'org/joda/',
                'org/joda/time/',
                'org/joda/time/tz/',
                'org/joda/time/tz/data/',
                'org/joda/time/tz/data/Africa/',
                'org/joda/time/tz/data/Africa/Abidjan',
                'org/gradle/MyAClass.class',
                'org/gradle/MyBClass.class',
                'org/gradle/MyFirstClass.class',
                'META-INF/',
                'META-INF/services/',
                'META-INF/services/org.gradle.internal.service.scopes.PluginServiceRegistry',
                'META-INF/services/org.gradle.internal.other.Service',
                'META-INF/.gradle-runtime-shaded']
        }
        outputJar.md5Hash == "55b2497496d71392a4fa9010352aaf38"
    }

    def "excludes module-info.class from jar"() {
        given:

        def inputFilesDir = tmpDir.createDir('inputFiles')
        def jarFile1 = inputFilesDir.file('lib1.jar')
        createJarFileWithClassFiles(jarFile1, ['org/gradle/MyClass'])
        def jarFile2 = inputFilesDir.file('lib2.jar')
        createJarFileWithClassFiles(jarFile2, ['org/gradle/MySecondClass', 'module-info'])
        def inputDirectory = inputFilesDir.createDir('dir1')
        writeClass(inputDirectory, "org/gradle/MyFirstClass")

        when:
        relocatedJarCreator.create(outputJar, [jarFile1, jarFile2, inputDirectory])

        then:

        TestFile[] contents = tmpDir.testDirectory.listFiles().findAll { it.isFile() }
        contents.length == 1
        contents[0] == outputJar
        handleAsJarFile(outputJar) { JarFile file ->
            List<JarEntry> entries = file.entries() as List
            assert entries*.name == [
                'org/',
                'org/gradle/',
                'org/gradle/MyClass.class',
                'org/gradle/MySecondClass.class',
                'org/gradle/MyFirstClass.class',
                'META-INF/',
                'META-INF/.gradle-runtime-shaded']
        }
    }

    def "merges provider-configuration file with the same name"() {
        given:
        def inputFilesDir = tmpDir.createDir('inputFiles')
        def serviceType = 'org.gradle.internal.service.scopes.PluginServiceRegistry'
        def jarFile1 = inputFilesDir.file('lib1.jar')
        createJarFileWithProviderConfigurationFile(jarFile1, serviceType, 'org.gradle.api.internal.artifacts.DependencyServices')
        def jarFile2 = inputFilesDir.file('lib2.jar')
        createJarFileWithProviderConfigurationFile(jarFile2, serviceType, """

org.gradle.plugin.use.internal.PluginUsePluginServiceRegistry

""")
        def jarFile3 = inputFilesDir.file('lib3.jar')
        createJarFileWithProviderConfigurationFile(jarFile3, serviceType, """
# This is some same file
# Ignore comment
org.gradle.api.internal.tasks.CompileServices
# Too many comments""")

        when:
        relocatedJarCreator.create(outputJar, [jarFile1, jarFile2, jarFile3])

        then:
        TestFile[] contents = tmpDir.testDirectory.listFiles().findAll { it.isFile() }
        contents.length == 1
        def relocatedJar = contents[0]
        relocatedJar == outputJar

        handleAsJarFile(relocatedJar) { JarFile jar ->
            JarEntry providerConfigJarEntry = jar.getJarEntry("META-INF/services/$serviceType")
            IoActions.withResource(jar.getInputStream(providerConfigJarEntry), new Action<InputStream>() {
                void execute(InputStream inputStream) {
                    assert inputStream.text == """org.gradle.api.internal.artifacts.DependencyServices
org.gradle.plugin.use.internal.PluginUsePluginServiceRegistry
org.gradle.api.internal.tasks.CompileServices"""
                }
            })
        }
    }

    def "relocates Gradle impl dependency classes"() {
        given:
        def noRelocationClassNames = ['org/gradle/MyClass',
                                      'java/lang/String',
                                      'javax/inject/Inject',
                                      'groovy/util/XmlSlurper',
                                      'groovyjarjarantlr/TokenStream',
                                      'net/rubygrapefruit/platform/FileInfo',
                                      'org/codehaus/groovy/ant/Groovyc',
                                      'org/apache/tools/ant/taskdefs/Ant',
                                      'org/slf4j/Logger',
                                      'org/apache/commons/logging/Log',
                                      'org/apache/log4j/Logger',
                                      'org/apache/xerces/parsers/SAXParser',
                                      'org/w3c/dom/Document',
                                      'org/xml/sax/XMLReader']
        def relocationClassNames = ['org/apache/commons/lang/StringUtils',
                                    'com/google/common/collect/Lists']
        def classNames = noRelocationClassNames + relocationClassNames
        def inputFilesDir = tmpDir.createDir('inputFiles')
        def jarFile = inputFilesDir.file('lib.jar')
        createJarFileWithClassFiles(jarFile, classNames)

        when:
        relocatedJarCreator.create(outputJar, [jarFile])

        then:
        TestFile[] contents = tmpDir.testDirectory.listFiles().findAll { it.isFile() }
        contents.length == 1
        def relocatedJar = contents[0]
        relocatedJar == outputJar

        handleAsJarFile(relocatedJar) { JarFile jar ->
            assert jar.getJarEntry('org/gradle/MyClass.class')
            assert jar.getJarEntry('java/lang/String.class')
            assert jar.getJarEntry('javax/inject/Inject.class')
            assert jar.getJarEntry('groovy/util/XmlSlurper.class')
            assert jar.getJarEntry('groovyjarjarantlr/TokenStream.class')
            assert jar.getJarEntry('net/rubygrapefruit/platform/FileInfo.class')
            assert jar.getJarEntry('org/codehaus/groovy/ant/Groovyc.class')
            assert jar.getJarEntry('org/apache/tools/ant/taskdefs/Ant.class')
            assert jar.getJarEntry('org/slf4j/Logger.class')
            assert jar.getJarEntry('org/apache/commons/logging/Log.class')
            assert jar.getJarEntry('org/apache/log4j/Logger.class')
            assert jar.getJarEntry('org/apache/xerces/parsers/SAXParser.class')
            assert jar.getJarEntry('org/w3c/dom/Document.class')
            assert jar.getJarEntry('org/xml/sax/XMLReader.class')
            assert jar.getJarEntry('org/gradle/internal/impldep/org/apache/commons/lang/StringUtils.class')
            assert jar.getJarEntry('org/gradle/internal/impldep/com/google/common/collect/Lists.class')
        }
    }

    def "remaps old-style string class literals"() {
        given:
        def clazz = IvySettings
        byte[] classData = clazz.getClassLoader().getResourceAsStream("${clazz.name.replace('.', '/')}.class").bytes

        when:
        def remapped = relocatedJarCreator.remapClass(clazz.name, classData)
        def cr = new ClassReader(remapped)
        def writer = new StringWriter()
        def tcv = new TraceClassVisitor(new PrintWriter(writer))
        cr.accept(tcv, 0)

        then:
        def bytecode = writer.toString()
        !bytecode.contains('LDC "org.apache.ivy.core.settings.XmlSettingsParser"')
        //bytecode.contains('static synthetic Ljava/lang/Class; class$org$gradle$internal$impldep$org$apache$ivy$core$settings$IvySettings') //TODO: this sucks, test shouldn't rely on external code, which changes unexpectedly
        bytecode.contains('GETSTATIC org/gradle/internal/impldep/org/apache/ivy/plugins/matcher/ExactPatternMatcher.INSTANCE : Lorg/gradle/internal/impldep/org/apache/ivy/plugins/matcher/ExactPatternMatcher;')
        bytecode.contains('LDC Lorg/gradle/internal/impldep/org/apache/ivy/core/settings/IvySettings;.class')
    }

    def "remaps class literals in strings"() {
        given:
        def clazz = IvySettings
        byte[] classData = clazz.getClassLoader().getResourceAsStream("${clazz.name.replace('.', '/')}.class").bytes

        when:
        def remapped = relocatedJarCreator.remapClass(clazz.name, classData)
        def cr = new ClassReader(remapped)
        def writer = new StringWriter()
        def tcv = new TraceClassVisitor(new PrintWriter(writer))
        cr.accept(tcv, 0)

        then:
        def bytecode = writer.toString()
        !bytecode.contains('LDC "org.apache.ivy.plugins.matcher.GlobPatternMatcher"')
        bytecode.contains('LDC "org.gradle.internal.impldep.org.apache.ivy.plugins.matcher.GlobPatternMatcher"')
    }

    def "remaps class literals in strings with slashes"() {
        given:
        def clazz = JavaAdapter
        byte[] classData = clazz.getClassLoader().getResourceAsStream("${clazz.name.replace('.', '/')}.class").bytes

        when:
        def remapped = relocatedJarCreator.remapClass(clazz.name, classData)
        def cr = new ClassReader(remapped)
        def writer = new StringWriter()
        def tcv = new TraceClassVisitor(new PrintWriter(writer))
        cr.accept(tcv, 0)

        then:
        def bytecode = writer.toString()
        !bytecode.contains('LDC "com/google/common/base/Joiner"')
        bytecode.contains('LDC "org/gradle/internal/impldep/com/google/common/base/Joiner"')
    }

    def "ignores slf4j logger bindings"() {
        given:
        def inputFilesDir = tmpDir.createDir('inputFiles')
        def jarFile = inputFilesDir.file('lib.jar')
        createJarFileWithClassFiles(jarFile, ["org.slf4j.impl.StaticLoggerBinder"])

        when:
        relocatedJarCreator.create(outputJar, [jarFile])

        then:
        handleAsJarFile(outputJar) {
            it.getEntry("org/slf4j/impl/StaticLoggerBinder.class")
        }
    }

    def "remaps resources"() {
        given:
        def noRelocationResources = ['org/gradle/reporting/report.js',
                                     'net/rubygrapefruit/platform/osx-i386/libnative-platform.dylib',
                                     'org/joda/time/tz/data/Africa/Abidjan']
        def onlyRelocatedResources = [] // None
        def generatedFiles = [GradleRuntimeShadedJarDetector.MARKER_FILENAME]
        def resources = noRelocationResources + onlyRelocatedResources
        def directories = ['net/',
                           'net/rubygrapefruit/',
                           'net/rubygrapefruit/platform/',
                           'net/rubygrapefruit/platform/osx-i386/',
                           'org/',
                           'org/gradle/',
                           'org/gradle/reporting/',
                           'org/joda/',
                           'org/joda/time/',
                           'org/joda/time/tz/',
                           'org/joda/time/tz/data/',
                           'org/joda/time/tz/data/Africa/',
                           'META-INF/']
        def inputFilesDir = tmpDir.createDir('inputFiles')
        def jarFile = inputFilesDir.file('lib.jar')
        createJarFileWithResources(jarFile, resources)

        when:
        relocatedJarCreator.create(outputJar, [jarFile])

        then:
        TestFile[] contents = tmpDir.testDirectory.listFiles().findAll { it.isFile() }
        contents.length == 1
        def relocatedJar = contents[0]
        relocatedJar == outputJar

        handleAsJarFile(relocatedJar) { JarFile jar ->
            assert jar.entries().toList().size() ==
                noRelocationResources.size() +
                onlyRelocatedResources.size() +
                generatedFiles.size() +
                directories.size()
            noRelocationResources.each { resourceName ->
                assert jar.getEntry(resourceName)
            }
            onlyRelocatedResources.each { resourceName ->
                assert jar.getEntry("org/gradle/internal/impldep/$resourceName")
            }
            generatedFiles.each { resourceName ->
                assert jar.getEntry(resourceName)
            }
        }
    }

    @Issue("https://github.com/gradle/gradle/issues/11027")
    def "relocates multiple third-party impl dependency service providers in the same provider-configuration file"() {
        given:
        def inputFilesDir = tmpDir.createDir('inputFiles')
        def serviceType = 'java.util.spi.ToolProvider'
        def jarFile = inputFilesDir.file('lib1.jar')
        def multiLineProviders = 'org.junit.JarToolProvider\norg.jetbrains.ide.JavadocToolProvider\nbsh.Main'
        createJarFileWithProviderConfigurationFile(jarFile, serviceType, multiLineProviders)

        when:
        relocatedJarCreator.create(outputJar, [jarFile])

        then:
        TestFile[] contents = tmpDir.testDirectory.listFiles().findAll { it.isFile() }
        contents.length == 1
        def relocatedJar = contents[0]
        relocatedJar == outputJar

        handleAsJarFile(relocatedJar) { JarFile jar ->
            JarEntry providerConfigJarEntry = jar.getJarEntry("META-INF/services/$serviceType")
            IoActions.withResource(jar.getInputStream(providerConfigJarEntry), new Action<InputStream>() {
                void execute(InputStream inputStream) {
                    assert inputStream.text == "org.gradle.internal.impldep.org.junit.JarToolProvider\norg.gradle.internal.impldep.bsh.Main"
                }
            })
        }
    }

    private void createJarFileWithClassFiles(TestFile jar, List<String> classNames) {
        TestFile contents = tmpDir.createDir("contents/$jar.name")

        classNames.each { className ->
            writeClass(contents, className)
        }

        contents.zipTo(jar)
    }

    private static void writeClass(TestFile outputDir, String className) {
        TestFile classFile = outputDir.createFile("${className}.class")
        ClassNode classNode = new ClassNode()
        classNode.version = className == 'module-info' ? Opcodes.V9 : Opcodes.V1_6
        classNode.access = Opcodes.ACC_PUBLIC
        classNode.name = className
        classNode.superName = 'java/lang/Object'

        ClassWriter cw = new ClassWriter(0)
        classNode.accept(cw)

        classFile.withOutputStream {
            it.write(cw.toByteArray())
        }
    }

    private void createJarFileWithProviderConfigurationFile(TestFile jar, String serviceType, String serviceProvider) {
        TestFile contents = tmpDir.createDir("contents/$jar.name")
        contents.createFile("META-INF/services/$serviceType") << serviceProvider
        contents.zipTo(jar)
    }

    private void createJarFileWithResources(TestFile jar, List<String> resourceNames) {
        TestFile contents = tmpDir.createDir("contents/$jar.name")
        resourceNames.each { resourceName ->
            contents.createFile(resourceName) << resourceName
        }
        contents.zipTo(jar)
    }

    static void handleAsJarFile(File jar, @ClosureParams(value = SimpleType, options = ["java.util.jar.JarFile"]) Closure<?> c) {
        new JarFile(jar).withCloseable(c)
    }

    static class JavaAdapter {
        // emulates what is found in org.mozilla.javascript.JavaAdapter
        // (we don't use it directly because we run this test against the 'core' distribution)
        void foo() {
            String[] classes = ['com/google/common/base/Joiner']
        }
    }
}
