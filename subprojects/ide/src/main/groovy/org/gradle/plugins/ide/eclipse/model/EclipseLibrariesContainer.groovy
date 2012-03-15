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

package org.gradle.plugins.ide.eclipse.model

import org.gradle.plugins.ide.eclipse.model.internal.Warnings

/**
 * Configures the libraries container for the wtp plugin
 */
class EclipseLibrariesContainer {

    /**
     * Whether the container behavior is enabled. False by default.
     */
    boolean enabled = false

    /**
     * The name of the container. The 'war' + 'eclipse-wtp' combo configures it so that it will
     * enable 'Web app libraries' container in Eclipse WTP.
     */
    String container = Warnings.CONTAINER_NOT_CONFIGURED

    /**
     * Whether the container should be exported.
     */
    boolean exported = false

    /**
     * Whether the container should replace explicit classpath entries from the .classpath.
     * 'true' means that no jar/project dependencies will be configured in the .classpath,
     * only the containers (java, web, etc.).
     */
    boolean replacesClasspath = false

    /**
     * @return if the .classpath should be cleared
     */
    protected boolean shouldReplaceClasspath() {
        enabled && replacesClasspath
    }
}