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

package org.gradle.api.publication

import org.gradle.api.internal.ConventionTask
import org.gradle.api.publication.maven.internal.ant.DefaultMavenPublisher
import org.gradle.api.tasks.TaskAction
import org.gradle.api.internal.file.TemporaryFileProvider

/**
 * @author: Szczepan Faber, created at: 6/16/11
 * @deprecated Replaced by the new publications model.
 */
@Deprecated
class InstallPublications extends ConventionTask {

    Publications publications

    @TaskAction
    void publish() {
        DefaultMavenPublisher publisher = new DefaultMavenPublisher(services.get(TemporaryFileProvider))
        publisher.install(publications.maven)
    }
}
