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

package org.gradle.jvm.toolchain.internal.install

import org.gradle.api.JavaVersion
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.temp.GradleUserHomeTemporaryFileProvider
import org.gradle.api.provider.Property
import org.gradle.cache.FileLock
import org.gradle.cache.FileLockManager
import org.gradle.cache.LockOptions
import org.gradle.initialization.GradleUserHomeDirProvider
import org.gradle.internal.jvm.inspection.JvmInstallationMetadata
import org.gradle.internal.jvm.inspection.JvmMetadataDetector
import org.gradle.internal.jvm.inspection.JvmVendor
import org.gradle.internal.os.OperatingSystem
import org.gradle.jvm.toolchain.JavaLanguageVersion
import org.gradle.jvm.toolchain.JavaToolchainSpec
import org.gradle.jvm.toolchain.JvmImplementation
import org.gradle.jvm.toolchain.JvmVendorSpec
import org.gradle.jvm.toolchain.internal.InstallationLocation
import org.gradle.util.internal.Resources
import org.junit.Rule
import spock.lang.Ignore
import spock.lang.Specification
import spock.lang.TempDir

import static org.junit.Assume.assumeTrue

class DefaultJdkCacheDirectoryTest extends Specification {

    @TempDir
    public File temporaryFolder

    @Rule
    public final Resources resources = new Resources()

    def "handles non-existing jdk directory when listing java homes"() {
        given:
        def jdkCacheDirectory = new DefaultJdkCacheDirectory(newHomeDirProvider(), Mock(FileOperations), mockLockManager(), mockDetector(), tmpFileProvider())


        when:
        def homes = jdkCacheDirectory.listJavaHomes()

        then:
        homes.isEmpty()
    }

    def "list java homes on MacOs"() {
        assumeTrue(OperatingSystem.current().isMacOsX())

        given:

        def jdkCacheDirectory = new DefaultJdkCacheDirectory(newHomeDirProvider(), Mock(FileOperations), mockLockManager(), mockDetector(), tmpFileProvider())

        def install1 = new File(temporaryFolder, "jdks/jdk-mac/Contents/Home").tap { mkdirs() }
        new File(temporaryFolder, "jdks/jdk-mac/" + DefaultJdkCacheDirectory.MARKER_FILE).createNewFile()

        def install2 = new File(temporaryFolder, "jdks/jdk-mac-2/some-jdk-folder/Contents/Home").tap { mkdirs() }
        new File(temporaryFolder, "jdks/jdk-mac-2/" + DefaultJdkCacheDirectory.MARKER_FILE).createNewFile()

        new File(temporaryFolder, "jdks/notReady").tap { mkdirs() }

        when:
        def homes = jdkCacheDirectory.listJavaHomes()

        then:
        homes == ([install1, install2] as Set)
    }

    def "lists only ready jdk directories when listing java homes"() {
        given:
        def jdkCacheDirectory = new DefaultJdkCacheDirectory(newHomeDirProvider(), Mock(FileOperations), mockLockManager(), mockDetector(), tmpFileProvider())

        def install1 = new File(temporaryFolder, "jdks/jdk-1").tap { mkdirs() }
        new File(install1, DefaultJdkCacheDirectory.MARKER_FILE).createNewFile()

        def install2 = new File(temporaryFolder, "jdks/jdk-2").tap { mkdirs() }
        new File(install2, DefaultJdkCacheDirectory.MARKER_FILE).createNewFile()

        new File(temporaryFolder, "jdks/notReady").tap { mkdirs() }

        when:
        def homes = jdkCacheDirectory.listJavaHomes()

        then:
        homes == ([install1, install2] as Set)
    }

    def "does not list jdk subdirectories when listing java homes"() {
        given:
        def jdkCacheDirectory = new DefaultJdkCacheDirectory(newHomeDirProvider(), Mock(FileOperations), mockLockManager(), mockDetector(), tmpFileProvider())

        def install1 = new File(temporaryFolder, "jdks/jdk-1/sub-folder").tap { mkdirs() }
        new File(install1, DefaultJdkCacheDirectory.MARKER_FILE).createNewFile()

        when:
        def homes = jdkCacheDirectory.listJavaHomes()

        then:
        homes.isEmpty()
    }

    def "does not list directories with only flaky legacy marker"() {
        given:
        def jdkCacheDirectory = new DefaultJdkCacheDirectory(newHomeDirProvider(), Mock(FileOperations), mockLockManager(), mockDetector(), tmpFileProvider())

        def install1 = new File(temporaryFolder, "jdks/jdk-1").tap { mkdirs() }
        new File(install1, DefaultJdkCacheDirectory.LEGACY_MARKER_FILE).createNewFile()

        def install2 = new File(temporaryFolder, "jdks/jdk-2/sub-folder").tap { mkdirs() }
        new File(install2, DefaultJdkCacheDirectory.LEGACY_MARKER_FILE).createNewFile()

        when:
        def homes = jdkCacheDirectory.listJavaHomes()

        then:
        homes.isEmpty()
    }

    def "provisions jdk from tar.gz archive"() {
        def jdkArchive = resources.getResource("jdk.tar.gz")
        def jdkCacheDirectory = new DefaultJdkCacheDirectory(newHomeDirProvider(), TestFiles.fileOperations(temporaryFolder, tmpFileProvider()), mockLockManager(), mockDetector(), tmpFileProvider())

        when:
        def installedJdk = jdkCacheDirectory.provisionFromArchive(mockSpec(), jdkArchive, URI.create("uri"))

        then:
        installedJdk.exists()
        installedJdk.getParentFile().getName() == "jdks"
        installedJdk.getName() == "ibm-11-arch-${os()}.2"
        new File(installedJdk, DefaultJdkCacheDirectory.LEGACY_MARKER_FILE).exists()
        new File(installedJdk, DefaultJdkCacheDirectory.MARKER_FILE).exists()
        new File(installedJdk, "jdk/file").exists()
    }

    def "provisions jdk from zip archive"() {
        def jdkArchive = resources.getResource("jdk.zip")

        def jdkCacheDirectory = new DefaultJdkCacheDirectory(newHomeDirProvider(), TestFiles.fileOperations(temporaryFolder, tmpFileProvider()), mockLockManager(), mockDetector(), tmpFileProvider())

        when:
        def installedJdk = jdkCacheDirectory.provisionFromArchive(mockSpec(), jdkArchive, URI.create("uri"))

        then:
        installedJdk.exists()
        installedJdk.getParentFile().getName() == "jdks"
        installedJdk.getName() == "ibm-11-arch-${os()}.2"
        new File(installedJdk, DefaultJdkCacheDirectory.LEGACY_MARKER_FILE).exists()
        new File(installedJdk, DefaultJdkCacheDirectory.MARKER_FILE).exists()
        new File(installedJdk, "jdk-123/file").exists()
    }

    @Ignore
    def "provisions jdk from tar.gz archive with MacOS symlinks"() {
        def jdkArchive = resources.getResource("jdk-with-symlinks.tar.gz")
        def jdkCacheDirectory = new DefaultJdkCacheDirectory(newHomeDirProvider(), TestFiles.fileOperations(temporaryFolder, tmpFileProvider()), mockLockManager(), mockDetector(), tmpFileProvider())

        when:
        def installedJdk = jdkCacheDirectory.provisionFromArchive(mockSpec(), jdkArchive, URI.create("uri"))

        then:
        installedJdk.exists()
        new File(installedJdk, "jdk-with-symlinks.2/bin/file").exists()

        //TODO: completely wrong; the uncompressed archive should look like this:
        // .
        // ├── bin -> zulu-11.jdk/Contents/Home/bin
        // ├── file
        // └── zulu-11.jdk
        //     └── Contents
        //         └── Home
        //             └── bin
        //                 └── file
        // but actually looks like this:
        // .
        // ├── bin
        // ├── file
        // └── zulu-11.jdk
        //     └── Contents
        //         └── Home
        //             └── bin
        //                 └── file
        // the symbolic link handling is AND HAS NOT BEEN WORKING
        // the test has been passing because it checks the existence of zulu-11.jdk/Contents/Home/bin/file, which has nothing to do with the symbolic link
    }

    private GradleUserHomeDirProvider newHomeDirProvider() {
        new GradleUserHomeDirProvider() {
            @Override
            File getGradleUserHomeDirectory() {
                return temporaryFolder
            }
        }
    }

    GradleUserHomeTemporaryFileProvider tmpFileProvider() {
        new GradleUserHomeTemporaryFileProvider(newHomeDirProvider())
    }

    FileLockManager mockLockManager() {
        def lockManager = Mock(FileLockManager)
        def lock = Mock(FileLock)
        lockManager.lock(_ as File, _ as LockOptions, _ as String, _ as String) >> lock
        lockManager
    }

    JvmMetadataDetector mockDetector() {
        JvmInstallationMetadata metadata = Mock(JvmInstallationMetadata)
        metadata.isValidInstallation() >> true
        metadata.getVendor() >> JvmVendor.KnownJvmVendor.IBM.asJvmVendor()
        metadata.getLanguageVersion() >> JavaVersion.VERSION_11
        metadata.getArchitecture() >> "arch"
        metadata.hasCapability(JvmInstallationMetadata.JavaInstallationCapability.J9_VIRTUAL_MACHINE) >> true

        def detector = Mock(JvmMetadataDetector)
        detector.getMetadata(_ as InstallationLocation) >> metadata
        detector
    }

    JavaToolchainSpec mockSpec() {
        Property<JavaLanguageVersion> javaLanguageVersionProperty = Mock(Property.class)
        javaLanguageVersionProperty.get() >> JavaLanguageVersion.of(11)

        Property<JvmImplementation> implementationProperty = Mock(Property.class)
        implementationProperty.get() >> JvmImplementation.J9

        Property<JvmVendorSpec> vendorProperty = Mock(Property.class)
        vendorProperty.get() >> JvmVendorSpec.IBM

        JavaToolchainSpec spec = Mock(JavaToolchainSpec)
        spec.getLanguageVersion() >> javaLanguageVersionProperty
        spec.getImplementation() >> implementationProperty
        spec.getVendor() >> vendorProperty
        spec.getDisplayName() >> "mock spec"
        spec
    }

    private static String os() {
        OperatingSystem os = OperatingSystem.current()
        return os.getFamilyName().replaceAll("[^a-zA-Z0-9\\-]", "_")
    }
}
