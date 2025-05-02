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
package org.gradle.tooling.internal.protocol;

import java.io.File;

/**
 * DO NOT CHANGE THIS INTERFACE. It is part of the cross-version protocol.
 *
 * <p>Consumer compatibility: This interface is used by all consumer versions from 1.0-milestone-3 to 1.0-milestone-7 to represent the model objects. Later consumer versions don't require that the
 * model objects implement this interface. </p>
 *
 * @since 1.0-milestone-3
 */
public interface ProjectVersion3 {
    /**
     * @since 1.0-milestone-3
     */
    String getPath();

    /**
     * @since 1.0-milestone-3
     */
    String getName();

    /**
     * @since 1.0-milestone-3
     */
    String getDescription();

    /**
     * @since 1.0-milestone-3
     */
    File getProjectDirectory();
}
