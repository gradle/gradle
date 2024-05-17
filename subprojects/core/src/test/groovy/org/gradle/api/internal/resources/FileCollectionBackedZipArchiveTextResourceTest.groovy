/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.resources

import com.google.common.base.Charsets
import org.gradle.api.internal.file.FileOperations
import org.gradle.api.internal.file.temp.TemporaryFileProvider
import org.gradle.api.internal.tasks.TaskDependencyFactory

class FileCollectionBackedZipArchiveTextResourceTest extends AbstractTextResourceTest {
    def setup() {
        def archive = project.file("archive.zip")
        def archiveEntry = project.file("archive/path/to/text")
        archiveEntry.parentFile.mkdirs()
        archiveEntry.text = "contents"
        project.ant.zip(basedir: project.file("archive"), destfile: archive)
        resource = new FileCollectionBackedArchiveTextResource(project.services.get(FileOperations), project.services.get(TaskDependencyFactory),
                project.services.get(TemporaryFileProvider), project.layout.files(archive), "path/to/text", Charsets.UTF_8)
    }
}
