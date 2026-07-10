/*
 * Copyright 2025 the original author or authors.
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

package gradlebuild.jvm

import org.gradle.api.NamedDomainObjectContainer
import org.gradle.api.NamedDomainObjectProvider
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSet

/**
 * A container of [JvmCompilation]s for a JVM project.
 *
 * Utility methods are provided to add compilations based on existing [SourceSet]s.
 */
abstract class JvmCompileExtension {

    companion object {
        const val NAME: String = "jvmCompile"
    }

    abstract val compilations: NamedDomainObjectContainer<JvmCompilation>

    fun Project.addCompilationFrom(sourceSet: NamedDomainObjectProvider<SourceSet>): JvmCompilation {
        return addCompilationFrom(sourceSet.get())
    }

    fun Project.addCompilationFrom(sourceSet: NamedDomainObjectProvider<SourceSet>, configure: JvmCompilation.() -> Unit): JvmCompilation {
        return addCompilationFrom(sourceSet.get(), configure)
    }

    fun Project.addCompilationFrom(sourceSet: SourceSet): JvmCompilation {
        return compilations.create(sourceSet.name) {
            from(sourceSet)
        }
    }

    fun Project.addCompilationFrom(sourceSet: SourceSet, configure: JvmCompilation.() -> Unit): JvmCompilation {
        return compilations.create(sourceSet.name) {
            from(sourceSet)
            configure()
        }
    }

}
