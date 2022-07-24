/*
 * Copyright 2020 the original author or authors.
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
package org.gradle.api.plugins;

import org.gradle.api.InvalidUserCodeException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.internal.DocumentationRegistry;

import javax.inject.Inject;

/**
 * The removed "maven" plugin class, only kept here for binary backwards compatibility
 * of plugins which use the `plugins.withPlugin` pattern.
 */
@Deprecated
public abstract class MavenPlugin implements Plugin<Project> {
    private final DocumentationRegistry documentationRegistry;

    @Inject
    public MavenPlugin(DocumentationRegistry documentationRegistry) {
        this.documentationRegistry = documentationRegistry;
    }

    @Override
    public void apply(Project target) {
        throw new InvalidUserCodeException(
            "The legacy `maven` plugin was removed in Gradle 7. Please use the `maven-publish` plugin instead. See " +
            documentationRegistry.getDocumentationFor("publishing_maven", "publishing_maven") + " for details");
    }
}
