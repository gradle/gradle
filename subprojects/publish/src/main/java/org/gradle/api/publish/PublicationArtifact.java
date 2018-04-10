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

package org.gradle.api.publish;

import org.gradle.api.Buildable;
import org.gradle.api.Incubating;

import javax.annotation.Nullable;
import java.io.File;

@Incubating
public interface PublicationArtifact extends Buildable {
    /**
     * The extension used to publish the artifact file, never <code>null</code>.
     * For an artifact without an extension, this value will be an empty String.
     */
    String getExtension();

    /**
     * The classifier used to publish the artifact file.
     * A <code>null</code> value (the default) indicates that this artifact will be published without a classifier.
     */
    @Nullable
    String getClassifier();

    /**
     * The name used to publish the artifact file, never <code>null</code>.
     * Defaults to the name of the module that this artifact belongs to.
     */
    String getName();

    /**
     * The type used to publish the artifact file, never <code>null</code>.
     * Often the type is the same as the extension, but sometimes this is not the case.
     * For example for an ivy XML module descriptor, the type is <em>ivy</em> and the extension is <em>xml</em>.
     */
    String getType();

    /**
     * The actual file contents to publish.
     */
    File getFile();

}
