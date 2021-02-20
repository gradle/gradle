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

package org.gradle.buildinit.plugins.internal;

import org.gradle.api.file.Directory;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.api.model.ObjectFactory;

/**
 * Converts some existing build to a Gradle build.
 */
public interface BuildConverter extends BuildInitializer {
    /**
     * Can this converter be applied to the contents of the current directory?
     */
    boolean canApplyToCurrentDirectory(Directory current);

    String getSourceBuildDescription();

    void configureClasspath(ProjectInternal.DetachedResolver detachedResolver, ObjectFactory objects);
}
