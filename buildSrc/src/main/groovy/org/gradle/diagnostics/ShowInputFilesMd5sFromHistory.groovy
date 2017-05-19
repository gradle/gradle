/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.diagnostics

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.internal.TaskInternal
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.changedetection.state.CacheBackedFileSnapshotRepository
import org.gradle.api.internal.changedetection.state.CacheBackedTaskHistoryRepository
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshot
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotter
import org.gradle.api.internal.changedetection.state.FileCollectionSnapshotterRegistry
import org.gradle.api.internal.changedetection.state.TaskHistoryRepository
import org.gradle.api.internal.changedetection.state.TaskHistoryStore
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.Optional
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.internal.id.RandomLongIdGenerator
import org.gradle.internal.scopeids.id.BuildScopeId
import org.gradle.internal.serialize.DefaultSerializerRegistry
import org.gradle.internal.serialize.SerializerRegistry

import javax.inject.Inject

class ShowInputFilesMd5sFromHistory extends DefaultTask {
    private final CacheBackedTaskHistoryRepository taskHistoryRepository

    @Input
    String taskPath

    @Input
    String propertyName

    @Optional
    @OutputFile
    File outputFile

    @Inject
    ShowInputFilesMd5sFromHistory(TaskHistoryStore cacheAccess, StringInterner stringInterner, BuildScopeId buildScopeId, FileCollectionSnapshotterRegistry fileCollectionSnapshotterRegistry) {
        SerializerRegistry serializerRegistry = new DefaultSerializerRegistry()
        for (FileCollectionSnapshotter snapshotter : fileCollectionSnapshotterRegistry.allSnapshotters) {
            snapshotter.registerSerializers(serializerRegistry)
        }
        taskHistoryRepository = new CacheBackedTaskHistoryRepository(
            cacheAccess,
            new CacheBackedFileSnapshotRepository(cacheAccess,
                serializerRegistry.build(FileCollectionSnapshot.class),
                new RandomLongIdGenerator()
            ),
            stringInterner,
            buildScopeId
        )
    }

    @TaskAction
    void load() {
        def task = (TaskInternal) project.tasks.getByPath(taskPath)
        TaskHistoryRepository.History history = taskHistoryRepository.getHistory(task)
        def execution = history.previousExecution
        if (!execution) {
            throw new GradleException("No previous execution for task '${taskPath}'")
        }
        FileCollectionSnapshot fileCollectionSnapshot = execution.inputFilesSnapshot.get(propertyName)
        if (!fileCollectionSnapshot) {
            throw new GradleException("Unknown file input property '${propertyName}' for task '${task.path}' of type '${task.class.superclass.simpleName}'. " +
                "Valid file input property names: ${execution.inputFilesSnapshot.keySet().join(', ')}")
        }
        if (outputFile) {
            outputFile.withPrintWriter { writer ->
                fileCollectionSnapshot.snapshots.each { absolutePath, normalizedFileSnapshot ->
                    writer.println("path: ${absolutePath}")
                    writer.println("normalized path: ${normalizedFileSnapshot.normalizedPath}")
                    writer.println("fingerprint: ${normalizedFileSnapshot.snapshot.contentMd5}")
                }
            }
        } else {
            fileCollectionSnapshot.snapshots.each { absolutePath, normalizedFileSnapshot ->
                logger.quiet("path: {}", absolutePath)
                logger.quiet("normalized path: {}", normalizedFileSnapshot.normalizedPath)
                logger.quiet("fingerprint: {}", normalizedFileSnapshot.snapshot.contentMd5)
            }
        }
    }
}
