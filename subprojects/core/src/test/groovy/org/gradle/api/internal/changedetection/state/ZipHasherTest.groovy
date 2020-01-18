/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.changedetection.state

import com.google.common.collect.ImmutableSet
import org.gradle.internal.hash.HashCode
import org.gradle.internal.snapshot.FileMetadata
import org.gradle.internal.snapshot.RegularFileSnapshot
import org.gradle.test.fixtures.file.TestFile
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import java.util.jar.Attributes
import java.util.jar.JarEntry
import java.util.jar.JarOutputStream
import java.util.jar.Manifest

class ZipHasherTest extends Specification {

    @Rule
    TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    ResourceEntryFilter manifestResourceFilter = new IgnoringResourceEntryFilter(ImmutableSet.copyOf("created-by"))
    ResourceEntryFilter propertyResourceFilter = new IgnoringResourceEntryFilter(ImmutableSet.copyOf("created-by"))
    ZipHasher zipHasher = new ZipHasher(new RuntimeClasspathResourceHasher(), ResourceFilter.FILTER_NOTHING, ResourceEntryFilter.FILTER_NOTHING, ResourceEntryFilter.FILTER_NOTHING)
    ZipHasher ignoringZipHasher = new ZipHasher(new RuntimeClasspathResourceHasher(), ResourceFilter.FILTER_NOTHING, manifestResourceFilter, propertyResourceFilter)

    def "adding an empty jar inside another jar changes the hashcode"() {
        given:
        def outerContent = tmpDir.createDir("outer")
        def outer = tmpDir.file("outer.jar")
        outerContent.zipTo(outer)
        def originalHash = zipHasher.hash(snapshot(outer))

        when:
        def innerContent = tmpDir.createDir("inner")
        def inner = outerContent.file("inner.jar")
        innerContent.zipTo(inner)
        outerContent.zipTo(outer)
        def newHash = zipHasher.hash(snapshot(outer))

        then:
        originalHash != newHash
    }

    def "relative path of nested zip entries is tracked"() {
        given:
        def outerContent1 = tmpDir.createDir("outer1")
        def innerContent1 = tmpDir.createDir("inner1")
        innerContent1.file("foo") << "Foo"
        def inner1 = outerContent1.file("inner1.jar")
        innerContent1.zipTo(inner1)
        def outer1 = tmpDir.file("outer1.jar")
        outerContent1.zipTo(outer1)
        def hash1 = zipHasher.hash(snapshot(outer1))

        def outerContent2 = tmpDir.createDir("outer2")
        def innerContent2 = tmpDir.createDir("inner2")
        innerContent2.file("foo") << "Foo"
        def inner2 = outerContent2.file("inner2.jar")
        innerContent2.zipTo(inner2)
        def outer2 = tmpDir.file("outer2.jar")
        outerContent2.zipTo(outer2)
        def hash2 = zipHasher.hash(snapshot(outer2))

        expect:
        hash1 != hash2
    }

    def "changing manifest attributes changes the hashcode"() {
        given:
        def jarfile = tmpDir.file("test.jar")
        createJarWithAttributes(jarfile, ["Implementation-Version": "1.0.0"])

        def jarfile2 = tmpDir.file("test2.jar")
        createJarWithAttributes(jarfile2, ["Implementation-Version": "1.0.1"])

        def hash1 = zipHasher.hash(snapshot(jarfile))
        def hash2 = zipHasher.hash(snapshot(jarfile2))

        expect:
        hash1 != hash2
    }

    def "manifest attributes are ignored"() {
        given:
        def jarfile = tmpDir.file("test.jar")
        createJarWithAttributes(jarfile, ["Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)"])

        def hash1 = zipHasher.hash(snapshot(jarfile))
        def hash2 = ignoringZipHasher.hash(snapshot(jarfile))

        expect:
        hash1 != hash2
    }

    def "manifest attributes are case insensitive"() {
        given:
        def jarfile = tmpDir.file("test.jar")
        createJarWithAttributes(jarfile, ["Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)"])

        def jarfile2 = tmpDir.file("test2.jar")
        createJarWithAttributes(jarfile2, ["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)"])

        when:

        def hash1 = zipHasher.hash(snapshot(jarfile))
        def hash2 = zipHasher.hash(snapshot(jarfile2))

        then:
        hash1 == hash2

        when:
        def hash3 = ignoringZipHasher.hash(snapshot(jarfile))
        def hash4 = ignoringZipHasher.hash(snapshot(jarfile2))

        then:
        hash3 == hash4
    }

    def "manifest attributes are section order insensitive"() {
        given:
        def jarfile = tmpDir.file("test.jar")
        def manifest = new Manifest()
        def attributes = manifest.getMainAttributes()
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
        attributes.put(new Attributes.Name("Created-By"), "1.8.0_232-b18 (Azul Systems, Inc.)")
        attributes.put(Attributes.Name.IMPLEMENTATION_VERSION, "1.0")

        def apiAttributes = new Attributes()
        apiAttributes.putValue("Sealed", "true")
        def baseAttributes = new Attributes()
        baseAttributes.putValue("Sealed", "true")

        manifest.entries.put("org/gradle/api", apiAttributes)
        manifest.entries.put("org/gradle/base", baseAttributes)

        JarOutputStream jarOutput = new JarOutputStream(jarfile.newOutputStream(), manifest);
        jarOutput.close()

        def jarfile2 = tmpDir.file("test2.jar")
        manifest = new Manifest()
        attributes = manifest.getMainAttributes()
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
        attributes.put(Attributes.Name.IMPLEMENTATION_VERSION, "1.0")
        attributes.put(new Attributes.Name("Created-By"), "1.8.0_232-b18 (Azul Systems, Inc.)")

        manifest.entries.put("org/gradle/base", baseAttributes)
        manifest.entries.put("org/gradle/api", apiAttributes)

        jarOutput = new JarOutputStream(jarfile2.newOutputStream(), manifest);
        jarOutput.close()

        def hash1 = zipHasher.hash(snapshot(jarfile))
        def hash2 = zipHasher.hash(snapshot(jarfile2))

        expect:
        hash1 == hash2
    }

    def "manifest section is ignored if all attributes are ignored"() {
        given:
        def jarfile = tmpDir.file("test.jar")
        def manifest = new Manifest()
        def attributes = manifest.getMainAttributes()
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")

        def apiAttributes = new Attributes()
        apiAttributes.putValue("Created-By", "1.8.0_232-b18 (Azul Systems, Inc.)")
        def baseAttributes = new Attributes()
        baseAttributes.putValue("Created-By", "1.8.0_232-b18 (Azul Systems, Inc.)")

        manifest.entries.put("org/gradle/api", apiAttributes)
        manifest.entries.put("org/gradle/base", baseAttributes)

        JarOutputStream jarOutput = new JarOutputStream(jarfile.newOutputStream(), manifest);
        jarOutput.close()

        def jarfile2 = tmpDir.file("test2.jar")
        createJarWithAttributes(jarfile2, [:])

        def hash1 = ignoringZipHasher.hash(snapshot(jarfile))
        def hash2 = ignoringZipHasher.hash(snapshot(jarfile2))

        expect:
        hash1 == hash2
    }

    def "changing manifest properties changes the hashcode"() {
        given:
        def jarfile = tmpDir.file("test.jar")
        createJarWithBuildInfo(jarfile, ["implementation-version": "1.0.0"])

        def jarfile2 = tmpDir.file("test2.jar")
        createJarWithBuildInfo(jarfile2, ["implementation-version": "1.0.1"])

        def hash1 = ignoringZipHasher.hash(snapshot(jarfile))
        def hash2 = ignoringZipHasher.hash(snapshot(jarfile2))

        expect:
        hash1 != hash2
    }

    def "manifest properties are case sensitive"() {
        given:
        def jarfile = tmpDir.file("test.jar")
        createJarWithBuildInfo(jarfile, ["Created-By": "1.8.0_232-b18 (Azul Systems, Inc.)"])

        def jarfile2 = tmpDir.file("test2.jar")
        createJarWithBuildInfo(jarfile2, ["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)"])

        def hash1 = zipHasher.hash(snapshot(jarfile))
        def hash2 = zipHasher.hash(snapshot(jarfile2))

        expect:
        hash1 != hash2
    }

    def "manifest properties are normalized and ignored"() {
        given:
        def jarfile = tmpDir.file("test.jar")
        createJarWithBuildInfo(jarfile, ["created-by": "1.8.0_232-b18 (Azul Systems, Inc.)", "foo": "true"], "Build information 1.0")

        def jarfile2 = tmpDir.file("test2.jar")
        createJarWithBuildInfo(jarfile2, ["created-by": "1.8.0_232-b15 (Azul Systems, Inc.)", "foo": "true"], "Build information 1.1")

        def hash1 = ignoringZipHasher.hash(snapshot(jarfile))
        def hash2 = ignoringZipHasher.hash(snapshot(jarfile2))

        expect:
        hash1 == hash2
    }

    def createJarWithAttributes(TestFile jarfile, Map<String, String> attributes) {
        def manifest = new Manifest()
        def mainAttributes = manifest.getMainAttributes()
        mainAttributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")
        attributes.each { name, value ->
            mainAttributes.put(new Attributes.Name(name), value)
        }
        new JarOutputStream(jarfile.newOutputStream(), manifest).close()
    }

    def createJarWithBuildInfo(TestFile jarfile, Map<String, String> props, String comments = "Build information") {
        def manifest = new Manifest()
        def attributes = manifest.getMainAttributes()
        attributes.put(Attributes.Name.MANIFEST_VERSION, "1.0")

        def properties = new Properties()
        props.each { name, value ->
            properties.put(name, value)
        }

        def jarOutput = new JarOutputStream(jarfile.newOutputStream(), manifest);
        def jarEntry = new JarEntry("META-INF/build-info.properties")
        jarOutput.putNextEntry(jarEntry)
        properties.store(jarOutput, comments)
        jarOutput.close()
    }

    private static RegularFileSnapshot snapshot(TestFile file) {
        new RegularFileSnapshot(file.path, file.name, HashCode.fromInt(0), new FileMetadata(0, 0))
    }
}
