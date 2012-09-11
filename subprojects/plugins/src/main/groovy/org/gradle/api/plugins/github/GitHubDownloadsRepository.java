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

package org.gradle.api.plugins.github;

import org.gradle.api.Incubating;
import org.gradle.api.artifacts.repositories.ArtifactRepository;
import org.gradle.api.artifacts.repositories.AuthenticationSupported;

import java.net.URI;

/**
 * A dependency repository that uses GitHub downloads as the source.
 *
 * A value for {@link #setUser(String)} must be provided before this can be used.
 * <p>
 * Given the following repository definition:
 * <pre>
 * repositories {
 *     github.downloads {
 *       user = "githubUser"
 *     }
 * }</pre>
 * <p>
 * The following dependency notations will resolve to:
 * <ul>
 * <li>{@code myProject:myThing} - {@code https://github.com/downloads/githubUser/myProject/myThing.jar}
 * <li>{@code myProject:myThing:1.0} - {@code https://github.com/downloads/githubUser/myProject/myThing-1.0.jar}
 * <li>{@code myProject:myThing@zip} - {@code https://github.com/downloads/githubUser/myProject/myThing.zip}
 * </ul>
 */
@Incubating
public interface GitHubDownloadsRepository extends ArtifactRepository, AuthenticationSupported {

    /**
     * {@value #DOWNLOADS_URL_BASE}
     */
    String DOWNLOADS_URL_BASE = "https://github.com/downloads";

    /**
     * Override the default base url of '{@value #DOWNLOADS_URL_BASE}'
     *
     * @param baseUrl The new base url
     */
    void setBaseUrl(Object baseUrl);

    /**
     * The base GitHub downloads url.
     *
     * Defaults to '{@value #DOWNLOADS_URL_BASE}'
     *
     * @return The base GitHub downloads url.
     */
    URI getBaseUrl();

    /**
     * Sets the GitHub user/organisation name that houses the downloads.
     *
     * Given a GitHub Downloads URL, this is the value at {@value #DOWNLOADS_URL_BASE}/«user».
     *
     * @param user The GitHub user/organisation name that houses the downloads.
     */
    void setUser(String user);

    /**
     * The GitHub user/organisation name that houses the downloads.
     *
     * @return The GitHub user/organisation name that houses the downloads.
     */
    String getUser();

}
