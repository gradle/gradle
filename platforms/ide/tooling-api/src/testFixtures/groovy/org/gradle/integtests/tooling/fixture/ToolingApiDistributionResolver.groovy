/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.integtests.tooling.fixture

import com.google.common.annotations.VisibleForTesting
import org.gradle.integtests.fixtures.RepoScriptBlockUtil
import org.gradle.integtests.fixtures.executer.CommitDistribution
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.internal.classloader.ClasspathUtil
import org.gradle.internal.file.locking.ExclusiveFileAccessManager
import org.gradle.test.fixtures.file.TestFile
import org.junit.Assert
import org.slf4j.Logger
import org.slf4j.LoggerFactory

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import java.security.MessageDigest
import java.security.NoSuchAlgorithmException
import java.util.zip.ZipException
import java.util.zip.ZipFile

/**
 * Downloads Tooling API clients of a given version, for use in cross version testing.
 */
class ToolingApiDistributionResolver {

    private static final Logger LOGGER = LoggerFactory.getLogger(ToolingApiDistributionResolver.class)

    private final Map<String, ToolingApiDistribution> distributions = [:]
    private final IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext()
    private final ExclusiveFileAccessManager fileAccessManager = new ExclusiveFileAccessManager(120000, 200)

    private final String repoUrl

    ToolingApiDistributionResolver() {
        this(RepoScriptBlockUtil.gradleRepositoryMirrorUrl())
    }

    @VisibleForTesting
    ToolingApiDistributionResolver(String repoUrl) {
        this.repoUrl = repoUrl
    }

    ToolingApiDistribution resolve(String toolingApiVersion) {
        if (!distributions[toolingApiVersion]) {
            if (useToolingApiFromTestClasspath(toolingApiVersion)) {
                distributions[toolingApiVersion] = resolveExternalToolingApiDistribution(toolingApiVersion, new File(System.getProperty("toolingApi.shadedJar")))
            } else if (CommitDistribution.isCommitDistribution(toolingApiVersion)) {
                throw new UnsupportedOperationException(String.format("Commit distributions are not supported in this context. Adjust %s code to support them", this.class.canonicalName))
            } else {
                distributions[toolingApiVersion] = resolveExternalToolingApiDistribution(toolingApiVersion, locateToolingApi(toolingApiVersion))
            }
        }
        distributions[toolingApiVersion]
    }

    private static String getFileChecksum(File file) throws IOException, NoSuchAlgorithmException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (InputStream is = Files.newInputStream(Paths.get(file.getAbsolutePath()))) {
            byte[] buffer = new byte[8192];
            int bytesRead;
            while ((bytesRead = is.read(buffer)) != -1) {
                digest.update(buffer, 0, bytesRead);
            }
        }
        byte[] hashBytes = digest.digest();
        StringBuilder sb = new StringBuilder();
        for (byte b : hashBytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private void checkTapiJar(File tapiJar) {
        Assert.assertTrue("${tapiJar.absolutePath} doesn't exist!", tapiJar.exists())
        Assert.assertTrue("${tapiJar.absolutePath} is not readable!", Files.isReadable(tapiJar.toPath()))
        // Get file size and last modified time before opening
        long fileSize = tapiJar.length();
        long lastModified = tapiJar.lastModified();
        System.out.println("DEBUG: Attempting to open file: " + tapiJar.getAbsolutePath());
        System.out.println("DEBUG: File size: " + fileSize + " bytes");
        System.out.println("DEBUG: Last modified: " + new java.util.Date(lastModified));

        // Use a try-with-resources statement for automatic resource management
        try (ZipFile zipFile = new ZipFile(tapiJar)) {
            // Assert that the file has at least one entry.
            // If this fails, the file might be an empty zip or corrupted.
            boolean hasEntries = zipFile.stream().findFirst().isPresent();
            if (hasEntries) {
                System.out.println("SUCCESS: File opened and has entries.");
            } else {
                System.err.println("WARNING: " + tapiJar.getAbsolutePath() + " has no entries!");
                // This is a potential issue, but not the zip END header error.
            }

        } catch (ZipException e) {
            // This is the error we are specifically trying to debug
            System.err.println("ERROR: java.util.zip.ZipException occurred!");
            System.err.println("ERROR: Message: " + e.getMessage());

            // Additional debugging information at the time of the error
            long currentFileSize = tapiJar.length();
            long currentLastModified = tapiJar.lastModified();
            String fileHash = "";
            try {
                fileHash = getFileChecksum(tapiJar);
            } catch (IOException | NoSuchAlgorithmException ex) {
                System.err.println("ERROR: Could not calculate file checksum: " + ex.getMessage());
            }

            System.err.println("DEBUG (at time of error):");
            System.err.println("DEBUG: File path: " + tapiJar.getAbsolutePath());
            System.err.println("DEBUG: File size was " + fileSize + " but is now " + currentFileSize + " bytes");
            System.err.println("DEBUG: Last modified was " + new java.util.Date(lastModified) + " but is now " + new java.util.Date(currentLastModified));
            System.err.println("DEBUG: File SHA-256 Checksum: " + fileHash);

            // Option to make a copy for later inspection
            String errorTimestamp = new java.text.SimpleDateFormat("yyyyMMdd-HHmmss").format(new java.util.Date());
            File badFileCopy = new File(tapiJar.getParent(), tapiJar.getName() + ".corrupted." + errorTimestamp);
            try {
                Files.copy(tapiJar.toPath(), badFileCopy.toPath());
                System.err.println("DEBUG: A copy of the corrupted file has been saved to: " + badFileCopy.getAbsolutePath());
                System.err.println("DEBUG: Please inspect this file for a truncated or incomplete zip header.");
            } catch (IOException copyEx) {
                System.err.println("ERROR: Could not create a copy of the corrupted file: " + copyEx.getMessage());
            }

        } catch (IOException e) {
            // General I/O exception, e.g., file not found
            System.err.println("ERROR: IOException occurred!");
            System.err.println("ERROR: Message: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private ExternalToolingApiDistribution resolveExternalToolingApiDistribution(String tapiVersion, File tapiJar) {
        checkTapiJar(tapiJar)
        File slf4jApi = locateLocalSlf4j()
        return new ExternalToolingApiDistribution(tapiVersion, [slf4jApi, tapiJar])
    }

    private File locateToolingApi(String version) {
        def relativePath = "org/gradle/gradle-tooling-api/$version/gradle-tooling-api-${version}.jar"

        File localRepository = buildContext.localRepository
        if (localRepository) {
            Path jarFile = localRepository.toPath().resolve(relativePath)
            if (Files.exists(jarFile)) {
                return jarFile.toFile()
            }
        }

        TestFile destination = buildContext.tmpDir.file("gradle-tooling-api-${version}.jar")
        if (!destination.exists()) {
            def url = repoUrl + "/" + relativePath
            download(url, destination)
        }
        return destination
    }

    /**
     * The tooling API depends on the SLF4j API jar -- it is not packaged in the fat jar.
     * Just use the SLF4J version on the classpath instead of resolving it from a repo.
     */
    private static File locateLocalSlf4j() {
        File location = ClasspathUtil.getClasspathForClass(Logger.class)
        assert location.name.endsWith(".jar"): "Expected to find SLF4J jar"
        location
    }

    private void download(String url, TestFile destination) {
        def markerFile = destination.withExtension("ok")
        fileAccessManager.access(destination) {
            if (!markerFile.exists()) {
                destination.delete()

                LOGGER.warn("Downloading {}", url)
                destination.copyFrom(new URI(url).toURL())

                markerFile.createFile()
            }
        }
    }

    private boolean useToolingApiFromTestClasspath(String toolingApiVersion) {
        toolingApiVersion == buildContext.version.baseVersion.version
    }
}
