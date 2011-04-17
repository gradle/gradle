/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.plugins.ide.eclipse.model

import org.gradle.util.ConfigureUtil

/**
 * DSL-friendly model of the Eclipse project information.
 * First point of entry when it comes to customizing the eclipse generation
 * <p>
 * See the examples in docs for {@link EclipseProject}
 * <p>
 * Author: Szczepan Faber, created at: 4/13/11
 */
class EclipseModel {

    EclipseProject project
    EclipseClasspath classpath

    void project(Closure closure) {
        ConfigureUtil.configure(closure, project)
    }

    void classpath(Closure closure) {
        ConfigureUtil.configure(closure, classpath)
    }
}
