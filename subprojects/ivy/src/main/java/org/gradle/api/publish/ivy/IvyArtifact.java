/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.publish.ivy;

import org.gradle.api.Incubating;

import java.io.File;

/**
 * An artifact published as part of a {@link IvyPublication}.
 */
@Incubating
public interface IvyArtifact {
    /**
     * The name used to publish the artifact file.
     */
    String getName();

    /**
     * Sets the name used to publish the artifact file.
     * @param name The name.
     */
    void setName(String name);

    /**
     * The type used to publish the artifact file.
     */
    String getType();

    /**
     * Sets the type used to publish the artifact file.
     * @param type The type.
     */
    void setType(String type);

    /**
     * The extension used to publish the artifact file.
     */
    String getExtension();

    /**
     * Sets the extension used to publish the artifact file.
     * @param extension The extension.
     */
    void setExtension(String extension);

    /**
     * The actual file contents to publish.
     */
    File getFile();
}
