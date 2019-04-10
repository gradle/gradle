/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.instantexecution.support

import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.internal.GradleInternal
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.internal.file.copy.CopySpecInternal
import org.gradle.api.internal.file.copy.DefaultCopySpec
import org.gradle.api.internal.file.copy.DestinationRootCopySpec
import org.gradle.api.internal.initialization.ClassLoaderIds
import org.gradle.api.internal.initialization.ClassLoaderScope
import org.gradle.api.internal.initialization.ScriptHandlerFactory
import org.gradle.api.internal.initialization.loadercache.ClassLoaderCache
import org.gradle.api.internal.project.IProjectFactory
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.initialization.ClassLoaderScopeRegistry
import org.gradle.initialization.DefaultProjectDescriptor
import org.gradle.initialization.DefaultSettings
import org.gradle.instantexecution.CoreSerializer
import org.gradle.instantexecution.InstantExecution
import org.gradle.internal.build.BuildState
import org.gradle.internal.classpath.ClassPath
import org.gradle.internal.file.PathToFileResolver
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.serialize.Decoder
import org.gradle.internal.serialize.Encoder
import org.gradle.internal.serialize.Serializer
import org.gradle.util.Path
import java.io.File
import java.util.ArrayList


class InstantExecutionHost internal constructor(private val gradle: GradleInternal) : InstantExecution.Host, CoreSerializer {

    private
    var classLoaderScopeRegistry: ClassLoaderScopeRegistry

    internal
    var scriptHandlerFactory: ScriptHandlerFactory

    private
    var projectFactory: IProjectFactory

    private
    val projectDescriptorRegistry
        get() = (gradle.settings as DefaultSettings).projectDescriptorRegistry

    override val coreSerializer: CoreSerializer
        get() = this

    override val scheduledTasks: List<Task>
        get() = gradle.taskGraph.allTasks

    private
    val coreAndPluginsScope: ClassLoaderScope
        get() = classLoaderScopeRegistry.coreAndPluginsScope

    init {
        classLoaderScopeRegistry = getService(ClassLoaderScopeRegistry::class.java)
        scriptHandlerFactory = getService(ScriptHandlerFactory::class.java)
        projectFactory = getService(IProjectFactory::class.java)
    }


    override fun <T> getService(serviceType: Class<T>): T {
        return gradle.services.get(serviceType)
    }

    override fun getSystemProperty(propertyName: String): String? {
        return gradle.startParameter.systemPropertiesArgs[propertyName]
    }

    override fun scheduleTasks(tasks: Iterable<Task>) {
        gradle.taskGraph.addEntryTasks(tasks)
    }

    override fun createProject(path: String): ProjectInternal {
        val projectPath = Path.path(path)

        val parentPath = projectPath.parent

        val name = projectPath.name
        val projectDescriptor = DefaultProjectDescriptor(
            getProjectDescriptor(parentPath), name ?: "instant-execution", File(".").absoluteFile,
            projectDescriptorRegistry,
            getService(PathToFileResolver::class.java)
        )
        return projectFactory.createProject(
            projectDescriptor, getProject(parentPath), gradle,
            coreAndPluginsScope,
            coreAndPluginsScope
        )
    }

    private
    fun getProject(parentPath: Path?): ProjectInternal? {
        return if (parentPath == null) null else gradle.rootProject.project(parentPath.path)
    }

    private
    fun getProjectDescriptor(parentPath: Path?): DefaultProjectDescriptor? {
        return if (parentPath == null) null else projectDescriptorRegistry.getProject(parentPath.path)
    }

    override fun classLoaderFor(classPath: ClassPath): ClassLoader {
        return getService(ClassLoaderCache::class.java).get(
            ClassLoaderIds.buildScript("instant-execution", "run"), classPath, coreAndPluginsScope.exportClassLoader, null
        )
    }

    override fun dependenciesOf(task: Task): Set<Task> {
        return gradle.taskGraph.getDependencies(task)
    }

    override fun getProject(projectPath: String): Project {
        return gradle.rootProject.project(projectPath)
    }

    override fun registerProjects() {
        getService(ProjectStateRegistry::class.java).registerProjects(getService(BuildState::class.java))
    }

    override fun serializerFor(value: Any): Function2<Encoder, Serializer<Any>, Unit>? {
        if (value is DefaultCopySpec) {
            return { encoder, objectSerializer ->
                val allSourcePaths = ArrayList<File>()
                collectSourcePathsFrom(value, allSourcePaths)
                encoder.writeByte(1.toByte())
                objectSerializer.write(encoder, allSourcePaths)
            }
        }
        if (value is DestinationRootCopySpec) {
            return { encoder, objectSerializer ->
                encoder.writeByte(2.toByte())
                objectSerializer.write(encoder, value.destinationDir)
                serializerFor(value.delegate)!!.invoke(encoder, objectSerializer)
            }
        }
        return null
    }

    private
    fun collectSourcePathsFrom(copySpec: DefaultCopySpec, files: MutableList<File>) {
        files.addAll(copySpec.resolveSourceFiles())
        for (child in copySpec.children) {
            collectSourcePathsFrom(child as DefaultCopySpec, files)
        }
    }

    override fun deserialize(decoder: Decoder, stateSerializer: Serializer<Any>): Any {
        when (decoder.readByte().toInt()) {
            1 -> {
                val sourceFiles = stateSerializer.read(decoder) as List<File>
                val copySpec = DefaultCopySpec(getService(FileResolver::class.java), getService(Instantiator::class.java))
                copySpec.from(sourceFiles)
                return copySpec
            }
            2 -> {
                val destDir = stateSerializer.read(decoder) as? File
                val delegate = deserialize(decoder, stateSerializer) as CopySpecInternal
                val spec = DestinationRootCopySpec(getService(PathToFileResolver::class.java), delegate)
                destDir?.let(spec::into)
                return spec
            }
            else -> throw IllegalStateException()
        }
    }
}
