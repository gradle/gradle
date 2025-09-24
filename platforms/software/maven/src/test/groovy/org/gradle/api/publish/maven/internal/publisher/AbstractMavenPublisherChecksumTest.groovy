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

package org.gradle.api.publish.maven.internal.publisher

import org.gradle.api.Action
import org.gradle.api.resources.ResourceException
import org.gradle.internal.resource.AbstractExternalResource
import org.gradle.internal.resource.ExternalResource
import org.gradle.internal.resource.ExternalResource.ContentAction
import org.gradle.internal.resource.ExternalResource.ContentAndMetadataAction
import org.gradle.internal.resource.ExternalResourceName
import org.gradle.internal.resource.ExternalResourceReadResult
import org.gradle.internal.resource.ExternalResourceRepository
import org.gradle.internal.resource.ExternalResourceWriteResult
import org.gradle.internal.resource.ReadableContent
import org.gradle.internal.resource.metadata.ExternalResourceMetaData
import spock.lang.Issue
import spock.lang.Specification
import spock.lang.TempDir

import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

class AbstractMavenPublisherChecksumTest extends Specification {
    @TempDir
    Path tempDir

    @Issue("https://github.com/gradle/gradle/issues/20232")
    def "checksums are generated for artifacts but not for signature files"() {
        given:
        def rootUri = tempDir.toUri()
        def publisher = new TestMavenPublisher(rootUri, "org.example", "artifact", "1.0")

        and: "create test artifact files"
        def artifacts = [
            // Artifact and its signature
            "artifact-1.0.jar",
            "artifact-1.0.jar.asc",
            // Sources jar and its signature
            "artifact-1.0-sources.jar", 
            "artifact-1.0-sources.jar.asc"
        ].collect { filename ->
            def path = tempDir.resolve(filename)
            if (!Files.exists(path)) {
                Files.createFile(path)
            }
            path.toFile()
        }

        when: "publishing artifacts and signatures"
        artifacts.each { artifact ->
            def destination = new ExternalResourceName(rootUri, artifact.name)
            publisher.publish(destination, artifact)
        }

        then: "generated files are created in the temp directory"
        def generatedFiles = Files.list(tempDir)
            .collect { it.fileName.toString() }

        and: "signature file checksums are not present"
        !generatedFiles.any { it in [
            // Artifact signature checksums
            "artifact-1.0.jar.asc.md5",
            "artifact-1.0.jar.asc.sha1",
            "artifact-1.0.jar.asc.sha256",
            "artifact-1.0.jar.asc.sha512",
            // Sources jar signature checksums
            "artifact-1.0-sources.jar.asc.md5",
            "artifact-1.0-sources.jar.asc.sha1",
            "artifact-1.0-sources.jar.asc.sha256",
            "artifact-1.0-sources.jar.asc.sha512",
        ] }

        and: "regular artifact, sources and their checksums exist"
        generatedFiles.containsAll([
            // Artifact
            "artifact-1.0.jar",
            "artifact-1.0.jar.asc",
            "artifact-1.0.jar.md5",
            "artifact-1.0.jar.sha1",
            "artifact-1.0.jar.sha256",
            "artifact-1.0.jar.sha512",
            // Sources
            "artifact-1.0-sources.jar",
            "artifact-1.0-sources.jar.asc",
            "artifact-1.0-sources.jar.md5",
            "artifact-1.0-sources.jar.sha1",
            "artifact-1.0-sources.jar.sha256",
            "artifact-1.0-sources.jar.sha512",
        ])
    }

    // ---------------------------------------------------------------------------------------------
    // Test helper classes
    // ---------------------------------------------------------------------------------------------

    private static class TestMavenPublisher extends AbstractMavenPublisher.ModuleArtifactPublisher {
        TestMavenPublisher(URI rootUri, String groupId, String artifactId, String moduleVersion) {
            super(new TestExternalResourceRepository(), false /*localRepo*/, rootUri, groupId, artifactId, moduleVersion)
        }
    }

    private static class TestExternalResourceRepository implements ExternalResourceRepository {
        @Override
        ExternalResourceRepository withProgressLogging() {
            return this
        }

        @Override
        ExternalResource resource(ExternalResourceName resource) {
            return this.resource(resource, false)
        }

        @Override
        ExternalResource resource(ExternalResourceName resource, boolean revalidate) {
            // Return a dummy ExternalResource that does nothing
            return new AbstractExternalResource() {
                @Override
                String getDisplayName() {
                    return "TestResource[${resource.uri}]"
                }

                @Override
                URI getURI() {
                    return resource.uri
                }

                @Override
                ExternalResourceReadResult<Void> writeToIfPresent(File destination) throws ResourceException {
                    // We don't care about this method in this test
                    return null
                }

                @Override
                ExternalResourceReadResult<Void> writeTo(OutputStream destination) throws ResourceException {
                    // We don't care about this method in this test
                    return ExternalResourceReadResult.of(0L, null)
                }

                @Override
                ExternalResourceReadResult<Void> withContent(Action<? super InputStream> readAction) {
                    // We don't care about this method in this test
                    return ExternalResourceReadResult.of(0L, null)
                }

                @Override
                <T> ExternalResourceReadResult<T> withContentIfPresent(ContentAction<? extends T> readAction) throws ResourceException {
                    // We don't care about this method in this test
                    return null
                }

                @Override
                <T> ExternalResourceReadResult<T> withContentIfPresent(ContentAndMetadataAction<? extends T> readAction) throws ResourceException {
                    // We don't care about this method in this test
                    return null
                }

                @Override
                ExternalResourceWriteResult put(ReadableContent source) throws ResourceException {
                    def destFile = new File(resource.uri)
                    source.open().withCloseable { sourceStream ->
                        // Ensure the parent directory exists
                        destFile.parentFile?.mkdirs()
                        // Copy the content to the destination file, replacing if it exists
                        Files.copy(sourceStream, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING)
                    }
                    return new ExternalResourceWriteResult(destFile.length())
                }

                @Override
                List<String> list() throws ResourceException {
                    // We don't care about this method in this test
                    return []
                }

                @Override
                ExternalResourceMetaData getMetaData() {
                    // We don't care about this method in this test
                    return null
                }
            }
        }
    }
}
