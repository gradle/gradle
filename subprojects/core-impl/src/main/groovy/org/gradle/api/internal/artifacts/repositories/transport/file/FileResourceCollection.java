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
package org.gradle.api.internal.artifacts.repositories.transport.file;

import org.apache.ivy.core.module.id.ArtifactRevisionId;
import org.apache.ivy.plugins.repository.Resource;
import org.apache.ivy.plugins.repository.file.FileRepository;
import org.gradle.api.internal.artifacts.repositories.transport.ResourceCollection;

import java.io.File;
import java.io.IOException;

public class FileResourceCollection extends FileRepository implements ResourceCollection {
    public Resource getResource(String source, ArtifactRevisionId artifactId) throws IOException {
        return getResource(source);
    }

    public Resource getResource(String source, ArtifactRevisionId artifactRevisionId, boolean forDownload) throws IOException {
        return getResource(source);
    }

    public void downloadResource(Resource res, File destination) throws IOException {
        get(res.getName(), destination);
    }
}
