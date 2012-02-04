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
package org.gradle.api.plugins.quality

import org.gradle.api.tasks.SourceSet

abstract class CodeQualityExtension {
    /**
     * The version of the code quality tool to be used.
     *
     * Example: toolVersion = "5.5"
     */
    String toolVersion

    /**
     * The source sets to be analyzed as part of the <tt>check</tt> and <tt>build</tt> tasks.
     * Defaults to <tt>project.sourceSets</tt> (i.e. all source sets in the project).
     *
     * Example: sourceSets = [project.sourceSets.main]
     */
    Collection<SourceSet> sourceSets

    /**
     * Whether or not to allow the build to continue if there are warnings. Defaults to <tt>false</tt>.
     *
     * Example: ignoreFailures = true
     */
    boolean ignoreFailures = false
}
