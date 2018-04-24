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
 * The SCM (source control management) of a Maven publication.
 *
 * @since 4.8
 * @see MavenPom
 */
@Incubating
public interface MavenPomScm {

    /**
     * Returns the connection URL of this SCM.
     */
    String getConnection();

    /**
     * Sets the connection URL of this SCM.
     */
    void setConnection(String connection);

    /**
     * Returns the developer connection URL of this SCM.
     */
    String getDeveloperConnection();

    /**
     * Sets the developer connection URL of this SCM.
     */
    void setDeveloperConnection(String developerConnection);

    /**
     * Returns the browsable repository URL of this SCM.
     */
    String getUrl();

    /**
     * Sets the browsable repository URL of this SCM.
     */
    void setUrl(String url);

    /**
     * Returns the tag of current code in this SCM.
     */
    String getTag();

    /**
     * Sets the tag of current code in this SCM.
     */
    void setTag(String tag);

}
