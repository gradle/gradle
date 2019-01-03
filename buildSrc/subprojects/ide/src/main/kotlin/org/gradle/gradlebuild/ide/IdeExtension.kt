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
package org.gradle.gradlebuild.ide

import org.gradle.api.Project

import accessors.idea


open class IdeExtension(private val project: Project) {

    /**
     * This is required if IDEA modules are imported without using the 'module per source set option'.
     * In this cases, there exists only one module per subproject. Inside this module, there is a fixed
     * number of classpath configurations (runtime, compile, test, ...) that is predefined by IDEA.
     * Thus there is no separate support for a `testFixtures` classpath. The `testFixtures` source folders
     * are treated as test source folders and thus we need everything on the test classpath in the
     * internal testing projects.
     *
     * Note: Because of all this the classpath is often not correct (too large). Eventually we should
     * remove this workaround and only use the `one modules per source set` import for the Gradle project.
     *
     * See: https://github.com/gradle/gradle-private/issues/1675
     */
    fun makeAllSourceDirsTestSourceDirsInIdeaModule(): Unit = project.run {
        idea {
            module {
                testSourceDirs = testSourceDirs + sourceDirs
                sourceDirs = emptySet()
            }
        }
    }
}
