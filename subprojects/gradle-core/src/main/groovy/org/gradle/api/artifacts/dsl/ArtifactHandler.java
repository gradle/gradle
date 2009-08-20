/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.artifacts.dsl;

/**
 * This class is for creating publish artifacts and adding them to configurations. Creating publish artifacts
 * does not mean to create an archive. What is created is a domain object which represents a file to be published
 * and information on how it should be published (e.g. the name). The publish artifact, that should be created,
 * can only be described with an archive at the moment. We will add additional notations in the future.
 *
 * <p>To create an publish artifact and assign it to a configuration you can use the following syntax:</p>
 *
 * <code>&lt;ArtifactHandler>.&lt;configurationName> &lt;archive1>, &lt;archive2>, ...</code>
 *
 *  <p>The information for publishing the artifact is extracted from the archive (e.g. name, extension, ...).</p>
 *
 * @author Hans Dockter
 */
public interface ArtifactHandler {
}
