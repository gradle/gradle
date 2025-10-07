/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.publish.maven.internal.artifact;

import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenPublication;
import org.jspecify.annotations.NullMarked;

/**
 * An artifact published as part of a {@link MavenPublication}.
 */
@NullMarked
public interface MavenArtifactInternal extends MavenArtifact {

    /**
     * If {@code true}, and if publishing to a remote Maven repository,
     * Gradle will generate checksum files for the artifact.
     * <p>
     * If {@code false}, checksum files will not be generated.
     * <p>
     * This is used to disable checksum files for {@code .asc} signature files,
     * because Maven Central does not require them:
     * <a href="https://central.sonatype.org/publish/requirements/#provide-file-checksums">https://central.sonatype.org/publish/requirements/#provide-file-checksums</a>.
     */
    boolean enableChecksumFileGeneration();
}
