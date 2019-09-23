/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.artifacts.repositories;

import org.gradle.api.Incubating;

import java.net.URI;

/**
 * A repository that supports resolving artifacts from a URL.
 *
 * @since 6.0
 */
@Incubating
public interface UrlArtifactRepository {

    /**
     * The base URL of this repository.
     *
     * @return The URL.
     */
    URI getUrl();

    /**
     * Sets the base URL of this repository.
     *
     * @param url The base URL.
     */
    void setUrl(URI url);

    /**
     * Sets the base URL of this repository.
     *
     * @param url The base URL.
     */
    void setUrl(Object url);

    /**
     * Specifies whether it is acceptable to communicate with a repository over an insecure HTTP connection.
     * <p>
     * For security purposes this intentionally requires a user to opt-in to using insecure protocols on case by case basis.
     * <p>
     * Gradle intentionally does not offer a global system/gradle property that allows a universal disable of this check.
     * <p>
     * <b>Allowing communication over insecure protocols allows for a man-in-the-middle to impersonate the intended server,
     * and gives an attacker the ability to
     * <a href="https://max.computer/blog/how-to-take-over-the-computer-of-any-java-or-clojure-or-scala-developer/">serve malicious executable code onto the system.</a>
     * </b>
     * <p>
     * See also:
     * <a href="https://medium.com/bugbountywriteup/want-to-take-over-the-java-ecosystem-all-you-need-is-a-mitm-1fc329d898fb">Want to take over the Java ecosystem? All you need is a MITM!</a>
     */
    boolean isAllowInsecureProtocol();

    /**
     * Specifies whether it is acceptable to communicate with a repository over an insecure HTTP connection.
     *
     * @see #isAllowInsecureProtocol()
     */
    void setAllowInsecureProtocol(boolean allowInsecureProtocol);
}
