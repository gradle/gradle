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

package org.gradle.tooling.internal.protocol;

/**
 * <p>DO NOT CHANGE THIS INTERFACE - it is part of the cross-version protocol.
 *
 * <p>Consumer compatibility: This interface is used by all consumer versions from 1.8-rc-1.</p>
 * <p>Provider compatibility: This interface is implemented by all provider versions from 1.8-rc-1.</p>
 *
 * @since 1.8-rc-1
 */
public interface InternalBuildController {
    /**
     * Returns the version-specific build model.
     *
     * <p>Consumer compatibility: This method is not used by any consumer versions.</p>
     * <p>Provider compatibility: This method is implemented by all provider versions from 1.8-rc-1.</p>
     *
     * @throws BuildExceptionVersion1 On build failure.
     * @since 1.8-rc-1
     */
    BuildResult<?> getBuildModel() throws BuildExceptionVersion1;

    /**
     * Returns the requested model for a target object.
     *
     * <p>Consumer compatibility: This method is used by all consumer versions from 1.8-rc-1.</p>
     * <p>Provider compatibility: This method is implemented by all provider versions from 1.8-rc-1.</p>
     *
     * @param target The target object. May be null, in which case a default target is used.
     * @param modelIdentifier The identifier of the model to build.
     * @throws BuildExceptionVersion1 On build failure.
     * @throws InternalUnsupportedModelException When the requested model is not supported.
     * @since 1.8-rc-1
     */
    BuildResult<?> getModel(Object target, ModelIdentifier modelIdentifier) throws BuildExceptionVersion1,
            InternalUnsupportedModelException;
}
