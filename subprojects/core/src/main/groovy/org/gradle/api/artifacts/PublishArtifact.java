/*
 * Copyright 2007 the original author or authors.
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

package org.gradle.api.artifacts;

import org.gradle.api.Buildable;

import java.io.File;
import java.util.Date;

/**
 * <p>A {@code PublishArtifact} is an artifact produced by a project.</p>
 *
 * @author Hans Dockter
 */
public interface PublishArtifact extends Buildable {
    /**
     * Returns the name of the artifact.
     */
    String getName();

    /**
     * Returns the extension of this published artifact. Often the extendsion is the same as the type,
     * but sometimes this is not the case. For example for an ivy xml module decsriptor, the type is
     * <em>ivy</em> and the extension is <em>xml</em>.
     *
     * @see #getType()
     */
    String getExtension();

    /**
     * Returns the type of the published artifact. Often the type is the same as the extension,
     * but sometimes this is not the case. For example for an ivy xml module decsriptor, the type is
     * <em>ivy</em> and the extension is <em>xml</em>.
     *
     * @see #getExtension()
     */
    String getType();

    /**
     * Returns the classifier of this published artifact. 
     */
    String getClassifier();

    /**
     * Returns the file of this artifact.
     */
    File getFile();

    /**
     * Returns the date that should be used when publishing this artifact. This is used
     * in the module descriptor accompanying this artifact (the ivy.xml). If the date is
     * not specified, the current date is used. If this artifact
     * is published without an module descriptor, this property has no relevance. 
     */
    Date getDate();
}
