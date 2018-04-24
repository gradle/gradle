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
 * A license of a Maven publication.
 *
 * @since 4.8
 * @see MavenPom
 * @see MavenPomLicenseSpec
 */
@Incubating
public interface MavenPomLicense {

    /**
     * Returns the name of this license.
     */
    String getName();

    /**
     * Sets the name of this license.
     */
    void setName(String name);

    /**
     * Returns the URL of this license.
     */
    String getUrl();

    /**
     * Sets the URL of this license.
     */
    void setUrl(String url);

    /**
     * Returns the distribution of this license.
     */
    String getDistribution();

    /**
     * Sets the distribution of this license.
     */
    void setDistribution(String distribution);

    /**
     * Returns the comments of this license.
     */
    String getComments();

    /**
     * Sets the comments of this license.
     */
    void setComments(String comments);

}
