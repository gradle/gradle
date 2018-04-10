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
     * The actual file contents to publish.
     */
    File getFile();

}
