/*
 * Copyright 2023 the original author or authors.
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

package gradlebuild.instrumentation.classpostprocessing

import org.gradle.api.Task
import org.gradle.api.file.Directory
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.TaskContainer
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.api.tasks.compile.JavaCompile


internal
fun SourceSet.configureSourceSet(tasks: TaskContainer, extension: PostProcessCompiledClassesExtension) {
    tasks.withType(JavaCompile::class.java).named(compileJavaTaskName) {
        configureTask(extension, AbstractCompile::getDestinationDirectory)
    }
}


internal
fun <T : Task> T.configureTask(extension: PostProcessCompiledClassesExtension, taskClassesDir: (T) -> Provider<Directory>) {
    inputs.property("classPostProcessors", extension.classPostProcessors)
    doLast {
        val postProcessors = extension.classPostProcessors.get()
        val classesDir = taskClassesDir(this@configureTask).get().asFile

        logger.debug("Post-processing compiled classes at $classesDir with processors: $postProcessors")
        postProcessClasses(classesDir, postProcessors)
    }
}
