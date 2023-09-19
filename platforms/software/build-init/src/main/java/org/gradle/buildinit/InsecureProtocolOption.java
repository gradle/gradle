/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.buildinit;

/**
 * Options for handling insecure protocols when generating a project with repositories.
 *
 * @since 7.3
 */
public enum InsecureProtocolOption {
    /**
     * Fail if a URL with an insecure protocol is found.
     *
     * Refuse to generate a Gradle build.
     */
    FAIL,

    /**
     * Emit a warning if a URL with an insecure protocol is found.
     *
     * The generated Gradle build will fail at runtime.
     */
    WARN,

    /**
     * Allow insecure protocols to be used when found.
     *
     * The generated Gradle build will not fail, but it will allow insecure protocols to be used.
     */
    ALLOW,

    /**
     * Upgrade an insecure protocol to a secure one.
     *
     * The generated Gradle build will not fail, but the repository may not be usable over a secure protocol.
     */
    UPGRADE;
}
