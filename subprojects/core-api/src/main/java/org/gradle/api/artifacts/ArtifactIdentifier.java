/*
 * Copyright 2011 the original author or authors.
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

import javax.annotation.Nullable;

/**
 * The identifier for a module artifact.
 *
 * @deprecated Will be removed in Gradle 9.0.
 */
@Deprecated
public interface ArtifactIdentifier {
    /**
     * Returns the identifier of the module that owns this artifact.
     */
    ModuleVersionIdentifier getModuleVersionIdentifier();

    /**
     * Returns the name of this artifact.
     */
    String getName();

    /**
     * Returns the type of this artifact. Often the type is the same as the extension,
     * but sometimes this is not the case. For example for an ivy XML module descriptor, the type is
     * <em>ivy</em> and the extension is <em>xml</em>.
     *
     * @see #getExtension()
     */
    String getType();

    /**
     * Returns the extension of this artifact. Often the extension is the same as the type,
     * but sometimes this is not the case. For example for an ivy XML module descriptor, the type is
     * <em>ivy</em> and the extension is <em>xml</em>.
     *
     * @see #getType()
     */
    @Nullable
    String getExtension();

    /**
     * Returns the classifier of this artifact, if any.
     */
    @Nullable
    String getClassifier();

}
