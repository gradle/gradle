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

import org.apache.commons.compress.archivers.tar.TarArchiveEntry
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.file.temp.GradleUserHomeTemporaryFileProvider
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
import org.gradle.jvm.toolchain.internal.DefaultToolchainSpec
import org.gradle.util.TestUtil
import spock.lang.Ignore
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.util.zip.GZIPOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

import static org.junit.Assume.assumeTrue

class DefaultJdkCacheDirectoryTest extends Specification {

    @TempDir
    public File temporaryFolder

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
        def jdkArchive = new File(temporaryFolder, "jdk.tar.gz")
        try (def outStream = new TarArchiveOutputStream(new GZIPOutputStream(jdkArchive.newOutputStream()))) {
            outStream.putArchiveEntry(new TarArchiveEntry("file"))
            outStream.closeArchiveEntry()

            outStream.putArchiveEntry(new TarArchiveEntry("folder/"))
            outStream.closeArchiveEntry()

            outStream.putArchiveEntry(new TarArchiveEntry("bin/" + OperatingSystem.current().getExecutableName("javac")))
            outStream.closeArchiveEntry()

            outStream.putArchiveEntry(new TarArchiveEntry("bin/" + OperatingSystem.current().getExecutableName("javadoc")))
            outStream.closeArchiveEntry()
        }
        def jdkCacheDirectory = new DefaultJdkCacheDirectory(newHomeDirProvider(), TestFiles.fileOperations(temporaryFolder, tmpFileProvider()), mockLockManager(), mockDetector(), tmpFileProvider())

        when:
        def installedJdk = jdkCacheDirectory.provisionFromArchive(mockSpec(), jdkArchive, URI.create("uri"))

        then:
        installedJdk.exists()
        installedJdk.getParentFile().getName() == "jdks"
        installedJdk.getName() == "ibm-11-arch-${os()}.2"
        new File(installedJdk, DefaultJdkCacheDirectory.LEGACY_MARKER_FILE).exists()
        new File(installedJdk, DefaultJdkCacheDirectory.MARKER_FILE).exists()
        new File(installedJdk, "file").exists()
        new File(installedJdk, "folder").isDirectory()
    }

    def "provisions jdk from zip archive"() {
        def jdkArchive = new File(temporaryFolder, "jdk.zip")
        try (def outStream = new ZipOutputStream(jdkArchive.newOutputStream())) {
            outStream.putNextEntry(new ZipEntry("file"))

            outStream.putNextEntry(new ZipEntry("folder/"))

            outStream.putNextEntry(new ZipEntry("bin/" + OperatingSystem.current().getExecutableName("javac")))

            outStream.putNextEntry(new ZipEntry("bin/" + OperatingSystem.current().getExecutableName("javadoc")))
        }
        def jdkCacheDirectory = new DefaultJdkCacheDirectory(newHomeDirProvider(), TestFiles.fileOperations(temporaryFolder, tmpFileProvider()), mockLockManager(), mockDetector(), tmpFileProvider())

        when:
        def installedJdk = jdkCacheDirectory.provisionFromArchive(mockSpec(), jdkArchive, URI.create("uri"))

        then:
        installedJdk.exists()
        installedJdk.getParentFile().getName() == "jdks"
        installedJdk.getName() == "ibm-11-arch-${os()}.2"
        new File(installedJdk, DefaultJdkCacheDirectory.LEGACY_MARKER_FILE).exists()
        new File(installedJdk, DefaultJdkCacheDirectory.MARKER_FILE).exists()
        new File(installedJdk, "file").exists()
        new File(installedJdk, "folder").isDirectory()
    }

    // This currently isn't working due to the linked issue
    @Ignore
    @Issue("https://github.com/gradle/gradle/issues/3982")
    def "provisions jdk from tar.gz archive with MacOS symlinks"() {
        def jdkArchive = new File(temporaryFolder, "jdk.tar.gz")
        try (def outStream = new TarArchiveOutputStream(new GZIPOutputStream(jdkArchive.newOutputStream()))) {
            // Symlink bin to the top level
            def binSymlink = new TarArchiveEntry("bin", TarArchiveEntry.LF_SYMLINK)
            binSymlink.linkName = "Contents/Home/bin"
            outStream.putArchiveEntry(binSymlink)
            outStream.closeArchiveEntry()

            outStream.putArchiveEntry(new TarArchiveEntry("folder/"))
            outStream.closeArchiveEntry()

            outStream.putArchiveEntry(new TarArchiveEntry("Contents/Home/bin/" + OperatingSystem.current().getExecutableName("javac")))
            outStream.closeArchiveEntry()

            outStream.putArchiveEntry(new TarArchiveEntry("Contents/Home/bin/" + OperatingSystem.current().getExecutableName("javadoc")))
            outStream.closeArchiveEntry()
        }
        def jdkCacheDirectory = new DefaultJdkCacheDirectory(newHomeDirProvider(), TestFiles.fileOperations(temporaryFolder, tmpFileProvider()), mockLockManager(), mockDetector(), tmpFileProvider())

        when:
        def installedJdk = jdkCacheDirectory.provisionFromArchive(mockSpec(), jdkArchive, URI.create("uri"))

        then:
        installedJdk.exists()
        installedJdk.getParentFile().getName() == "jdks"
        installedJdk.getName() == "ibm-11-arch-${os()}.2"
        new File(installedJdk, DefaultJdkCacheDirectory.LEGACY_MARKER_FILE).exists()
        new File(installedJdk, DefaultJdkCacheDirectory.MARKER_FILE).exists()
        Files.isSymbolicLink(new File(installedJdk, "bin").toPath())
        new File(installedJdk, "folder").isDirectory()
        new File(installedJdk, "Contents/Home/bin").isDirectory()
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
        (location) -> JvmInstallationMetadata.from(
            location.location,
            "11",
            JvmVendor.KnownJvmVendor.IBM.asJvmVendor().rawVendor,
            "",
            "",
            "J9",
            "",
            "",
            "arch"
        )
    }

    JavaToolchainSpec mockSpec() {
        JavaToolchainSpec spec = TestUtil.objectFactory().newInstance(DefaultToolchainSpec)
        spec.languageVersion.set(JavaLanguageVersion.of(11))
        spec.implementation.set(JvmImplementation.J9)
        spec.vendor.set(JvmVendorSpec.IBM)
        spec
    }

    private static String os() {
        OperatingSystem os = OperatingSystem.current()
        return os.getFamilyName().replaceAll("[^a-zA-Z0-9\\-]", "_")
    }
}
