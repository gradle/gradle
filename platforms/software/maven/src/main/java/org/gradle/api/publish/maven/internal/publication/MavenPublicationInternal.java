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

package org.gradle.api.publish.maven.internal.publication;

import org.gradle.api.Task;
import org.gradle.api.publish.internal.PublicationInternal;
import org.gradle.api.publish.maven.MavenArtifact;
import org.gradle.api.publish.maven.MavenPublication;
import org.gradle.api.publish.maven.internal.publisher.MavenNormalizedPublication;
import org.gradle.api.tasks.TaskProvider;

public interface MavenPublicationInternal extends MavenPublication, PublicationInternal<MavenArtifact> {

    @Override
    MavenPomInternal getPom();

    void setPomGenerator(TaskProvider<? extends Task> pomGenerator);

    void setModuleDescriptorGenerator(TaskProvider<? extends Task> moduleMetadataGenerator);

    MavenNormalizedPublication asNormalisedPublication();

    /**
     * Some components (e.g. Native) are published such that the module metadata references the original file name,
     * rather than the Maven-standard {artifactId}-{version}-{classifier}.{extension}.
     * This method enables this behaviour for the current publication.
     *
     *
     * <p>Note: This internal API is used by KMP:
     * <a href="https://github.com/JetBrains/kotlin/blob/5424c54fae7b4836506ec711edc0135392b445d6/libraries/tools/kotlin-gradle-plugin/src/common/kotlin/org/jetbrains/kotlin/gradle/plugin/mpp/Publishing.kt#L50">Usage</a></a>
     * </p>
     */
    void publishWithOriginalFileName();
}
