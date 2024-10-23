/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.classpath.transforms

import org.gradle.api.file.RelativePath
import org.gradle.api.internal.file.TestFiles
import org.gradle.internal.Pair
import org.gradle.model.internal.asm.AsmConstants
import org.gradle.internal.classloader.TransformReplacer
import org.gradle.internal.classpath.ClassData
import org.gradle.internal.classpath.ClasspathBuilder
import org.gradle.internal.classpath.ClasspathEntryVisitor
import org.gradle.internal.classpath.ClasspathWalker
import org.gradle.internal.classpath.DefaultClasspathBuilder
import org.gradle.internal.classpath.SystemPropertyAccessingThing
import org.gradle.internal.classpath.types.GradleCoreInstrumentationTypeRegistry
import org.gradle.internal.hash.Hasher
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import org.objectweb.asm.ClassVisitor
import spock.lang.Specification

import java.nio.charset.StandardCharsets
import java.util.function.BiFunction
import java.util.jar.JarFile

import static org.gradle.internal.classpath.transforms.ClasspathElementTransformTest.TransformFactoryType.AGENT
import static org.gradle.internal.classpath.transforms.ClasspathElementTransformTest.TransformFactoryType.LEGACY
import static org.gradle.util.JarUtils.jar

class ClasspathElementTransformTest extends Specification {
    @Rule
    TestNameTestDirectoryProvider testDirectoryProvider = new TestNameTestDirectoryProvider(getClass())
    def testDir = testDirectoryProvider.testDirectory
    def classpathBuilder = new DefaultClasspathBuilder(TestFiles.tmpDirTemporaryFileProvider(testDirectoryProvider.createDir("tmp")))
    def classpathWalker = new ClasspathWalker(TestFiles.fileSystem())
    def gradleCoreInstrumentingRegistry = Stub(GradleCoreInstrumentationTypeRegistry) {
        getInstrumentedTypesHash() >> Optional.empty()
        getUpgradedPropertiesHash() >> Optional.empty()
    }

    def "instrumentation for #factory preserves classes"() {
        given:
        def testFile = jar(testDir.file("thing.jar")) {
            manifest {}
            entry("Foo.class", classOne())
        }

        expect:
        with(transformJar(factory, testFile)) {
            if (expectManifest) {
                assertManifestPresentAndFirstEntry()
            }
            assertContainsFile("Foo.class")
        }

        where:
        factory | expectManifest
        AGENT   | false
        LEGACY  | true
    }

    def "agent instrumentation removes non multi-release manifest"() {
        given:
        def testFile = jar(testDir.file("thing.jar")) {
            manifest {
                mainAttributes.putValue("Some-Value", "theValue")
            }
            entry("Foo.class", classOne())
        }

        expect:
        with(transformJar(AGENT, testFile)) {
            assertNotContainsFile(JarFile.MANIFEST_NAME)
        }
    }

    def "legacy instrumentation copies manifest into transformed jar"() {
        given:
        def testFile = jar(testDir.file("thing.jar")) {
            manifest {
                mainAttributes.putValue("Some-Value", "theValue")
            }
            entry("Foo.class", classOne())
        }

        expect:
        with(transformJar(LEGACY, testFile)) {
            assertManifestPresentAndFirstEntry()

            assert manifest.mainAttributes.getValue("Some-Value") == "theValue"
        }
    }

    def "instrumentation for #factory produces multi-release jar from multi-release original"() {
        given:
        def testFile = jar(testDir.file("thing.jar")) {
            manifest {
                mainAttributes.putValue("Multi-Release", "true")
            }

            entry("Foo.class", classOne())
            versionedEntry(AsmConstants.MAX_SUPPORTED_JAVA_VERSION, "Foo.class", classOne())
        }

        expect:
        with(transformJar(factory, testFile)) {
            assertManifestPresentAndFirstEntry()

            assertIsMultiRelease()
            assertContainsFile("Foo.class")
            assertContainsVersioned(AsmConstants.MAX_SUPPORTED_JAVA_VERSION, "Foo.class")
        }

        where:
        factory << [AGENT, LEGACY]
    }

    def "instrumentation for #factory removes unsupported versioned directories from multi-release jar"() {
        given:
        def testFile = jar(testDir.file("thing.jar")) {
            manifest {
                mainAttributes.putValue("Multi-Release", "true")
            }

            entry("Foo.class", classOne())
            versionedEntry(AsmConstants.MAX_SUPPORTED_JAVA_VERSION + 1, "Foo.class", classOne())
        }

        expect:
        with(transformJar(factory, testFile)) {
            assertContainsFile("Foo.class")
            assertNotContainsVersioned(AsmConstants.MAX_SUPPORTED_JAVA_VERSION, "Foo.class")
        }

        where:
        factory << [AGENT, LEGACY]
    }

    def "agent instrumentation removes resources from transformed jar"() {
        given:
        def testFile = jar(testDir.file("thing.jar")) {
            manifest {}

            entry("Foo.class", classOne())
            entry("resource.txt", "")
        }

        expect:
        with(transformJar(AGENT, testFile)) {
            assertNotContainsFile("resource.txt")
        }
    }

    def "legacy instrumentation copies resources into transformed jar"() {
        given:
        def testFile = jar(testDir.file("thing.jar")) {
            manifest {}

            entry("Foo.class", classOne())
            entry("resource.txt", "resource body")
        }

        expect:
        with(transformJar(LEGACY, testFile)) {
            assertFileContent("resource.txt", "resource body")
        }
    }

    def "legacy instrumentation removes unsupported versioned resources from transformed jar"() {
        given:
        def testFile = jar(testDir.file("thing.jar")) {
            manifest {
                mainAttributes.putValue("Multi-Release", "true")
            }

            entry("Foo.class", classOne())
            entry("resource.txt", "resource body")
            versionedEntry(AsmConstants.MAX_SUPPORTED_JAVA_VERSION, "resource.txt", "resource MAX body")
            versionedEntry(AsmConstants.MAX_SUPPORTED_JAVA_VERSION + 1, "resource.txt", "resource unsupported body")
        }

        expect:
        with(transformJar(LEGACY, testFile)) {
            assertContainsVersioned(AsmConstants.MAX_SUPPORTED_JAVA_VERSION, "resource.txt")
            assertNotContainsVersioned(AsmConstants.MAX_SUPPORTED_JAVA_VERSION + 1, "resource.txt")
        }
    }

    def "multi-release jar must have manifest attribute to be processed by legacy instrumentation"() {
        given:
        def testFile = jar(testDir.file("thing.jar")) {
            manifest {
                // No Multi-Release attribute in the manifest.
            }

            entry("Foo.class", classOne())
            versionedEntry(AsmConstants.MAX_SUPPORTED_JAVA_VERSION + 1, "Foo.class", classOne())
        }

        expect:
        with(transformJar(LEGACY, testFile)) {
            assertContainsVersioned(AsmConstants.MAX_SUPPORTED_JAVA_VERSION + 1, "Foo.class")
        }
    }

    def "agent instrumentation puts no marker into non-multi-release jar"() {
        given:
        def testFile = jar(testDir.file("thing.jar")) {
            manifest {
                // No Multi-Release attribute in the manifest.
            }

            entry("Foo.class", classOne())
        }

        expect:
        with(transformJar(AGENT, testFile)) {
            assertNotContainsFile(TransformReplacer.MarkerResource.RESOURCE_NAME)
        }
    }

    def "agent instrumentation puts support marker to the root of multi-release jar"() {
        given:
        def testFile = jar(testDir.file("thing.jar")) {
            manifest {
                mainAttributes.putValue("Multi-Release", "true")
            }

            entry("Foo.class", classOne())
            versionedEntry(AsmConstants.MAX_SUPPORTED_JAVA_VERSION, "Foo.class", classOne())
        }

        expect:
        with(transformJar(AGENT, testFile)) {
            assertFileContent(TransformReplacer.MarkerResource.RESOURCE_NAME, instrumentedMarker())

            9..AsmConstants.MAX_SUPPORTED_JAVA_VERSION.each {
                assertNotContainsVersioned(it, TransformReplacer.MarkerResource.RESOURCE_NAME)
            }
        }
    }

    def "agent instrumentation puts support marker to unsupported versioned directory"() {
        given:
        def testFile = jar(testDir.file("thing.jar")) {
            manifest {
                mainAttributes.putValue("Multi-Release", "true")
            }

            entry("Foo.class", classOne())
            versionedEntry(AsmConstants.MAX_SUPPORTED_JAVA_VERSION + 1, "Foo.class", classOne())
            versionedEntry(AsmConstants.MAX_SUPPORTED_JAVA_VERSION + 2, "Foo.class", classOne())
        }

        expect:
        with(transformJar(AGENT, testFile)) {
            assertFileContent(TransformReplacer.MarkerResource.RESOURCE_NAME, instrumentedMarker())
            assertVersionedContent(AsmConstants.MAX_SUPPORTED_JAVA_VERSION + 1, TransformReplacer.MarkerResource.RESOURCE_NAME, notInstrumentedMarker())

            assertNotContainsVersioned(AsmConstants.MAX_SUPPORTED_JAVA_VERSION + 2, TransformReplacer.MarkerResource.RESOURCE_NAME)
        }
    }

    def "agent instrumentation puts no marker into unsupported versioned directories with resources only"() {
        given:
        def testFile = jar(testDir.file("thing.jar")) {
            manifest {
                mainAttributes.putValue("Multi-Release", "true")
            }

            entry("Foo.class", classOne())
            versionedEntry(AsmConstants.MAX_SUPPORTED_JAVA_VERSION + 1, "resource.txt", "resource")
        }

        expect:
        with(transformJar(AGENT, testFile)) {
            assertFileContent(TransformReplacer.MarkerResource.RESOURCE_NAME, instrumentedMarker())
            assertNotContainsVersioned(AsmConstants.MAX_SUPPORTED_JAVA_VERSION + 1, TransformReplacer.MarkerResource.RESOURCE_NAME)
        }
    }

    def "instrumentation for #factory converts directories to directories"() {
        given:
        def testDirectory = testDir.createDir("classes").create {
            file("Foo.class").bytes = classOne()
        }

        expect:
        with(transformDirectory(factory, testDirectory)) {
            assertIsDir()
            assertHasDescendants("Foo.class")
        }

        where:
        factory << [AGENT, LEGACY]
    }

    private enum TransformFactoryType {
        AGENT(ClasspathElementTransformFactoryForAgent::new),
        LEGACY(ClasspathElementTransformFactoryForLegacy::new);

        private final BiFunction<ClasspathBuilder, ClasspathWalker, ClasspathElementTransformFactory> factoryMaker

        TransformFactoryType(BiFunction<ClasspathBuilder, ClasspathWalker, ClasspathElementTransformFactory> factoryMaker) {
            this.factoryMaker = factoryMaker
        }

        ClasspathElementTransformFactory createFactory(ClasspathBuilder builder, ClasspathWalker walker) {
            return factoryMaker.apply(builder, walker)
        }

        @Override
        String toString() {
            return super.toString().toLowerCase(Locale.ENGLISH)
        }
    }

    private JarTestFixture transformJar(TransformFactoryType factory, File originalJar) {
        def outputJar = testDir.file("transformed.jar")
        return new JarTestFixture(transform(factory, originalJar, outputJar), 'UTF-8', null, /* checkManifest */ false)
    }

    private TestFile transformDirectory(TransformFactoryType factory, File originalDir) {
        def outputDir = testDir.file("transformed")
        return new TestFile(transform(factory, originalDir, outputDir))
    }

    private File transform(TransformFactoryType factory, File original, File target) {
        factory.createFactory(classpathBuilder, classpathWalker).createTransformer(original, new NoOpTransformer()).transform(target)
        return target
    }

    private static class NoOpTransformer implements ClassTransform {
        @Override
        void applyConfigurationTo(Hasher hasher) {
        }

        @Override
        Pair<RelativePath, ClassVisitor> apply(ClasspathEntryVisitor.Entry entry, ClassVisitor visitor, ClassData classData) {
            return Pair.of(entry.path, visitor)
        }
    }

    private byte[] classOne() {
        return getClass().classLoader.getResource(SystemPropertyAccessingThing.name.replace('.', '/') + ".class").bytes
    }

    private static String instrumentedMarker() {
        return markerBodyAsString(TransformReplacer.MarkerResource.TRANSFORMED)
    }

    private static String notInstrumentedMarker() {
        return markerBodyAsString(TransformReplacer.MarkerResource.NOT_TRANSFORMED)
    }

    private static String markerBodyAsString(TransformReplacer.MarkerResource resource) {
        return new String(resource.asBytes(), StandardCharsets.US_ASCII)
    }
}
