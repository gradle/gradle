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

package org.gradle.api.publish.maven;

import org.gradle.api.Incubating;

/**
 * The relocation information of a Maven publication that has been moved
 * to a new group and/or artifact ID.
 *
 * @since 4.8
 * @see MavenPom
 */
@Incubating
public interface MavenPomRelocation {

    /**
     * Returns the new group ID of the artifact.
     */
    String getGroupId();

    /**
     * Sets the new group ID of the artifact.
     */
    void setGroupId(String groupId);

    /**
     * Returns the new artifact ID of the artifact.
     */
    String getArtifactId();

    /**
     * Sets the new artifact ID of the artifact.
     */
    void setArtifactId(String artifactId);

    /**
     * Returns the new version of the artifact.
     */
    String getVersion();

    /**
     * Sets the new version of the artifact.
     */
    void setVersion(String version);

    /**
     * Returns the message to show the user for this relocation.
     */
    String getMessage();

    /**
     * Sets the message to show the user for this relocation.
     */
    void setMessage(String message);

}
