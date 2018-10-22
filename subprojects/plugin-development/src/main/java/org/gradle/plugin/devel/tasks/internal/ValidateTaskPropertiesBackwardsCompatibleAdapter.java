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

package org.gradle.plugin.devel.tasks.internal;

import org.gradle.api.file.FileCollection;

/**
 * This interface causes the creation of bridge methods in {@link org.gradle.plugin.devel.tasks.ValidateTaskProperties} to make the task backwards compatible with the pre 5.1 version.
 *
 * @deprecated Remove in Gradle 6.0.
 */
@Deprecated
public interface ValidateTaskPropertiesBackwardsCompatibleAdapter {
    FileCollection getClasses();
    FileCollection getClasspath();
}
