/*
 * Copyright 2013 the original author or authors.
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
package org.gradle.api.publish.ivy.internal.artifact;

import java.io.File;

public class FileIvyArtifact extends ConfigurableIvyArtifact {
    private final String name;
    private final String type;
    private final String extension;
    private final File file;

    public FileIvyArtifact(File file, String name, String type, String extension) {
        this.name = name;
        this.type = type;
        this.extension = extension;
        this.file = file;
    }

    @Override
    protected String getBaseName() {
        return name;
    }

    @Override
    protected String getBaseType() {
        return type;
    }

    @Override
    protected String getBaseExtension() {
        return extension;
    }

    public File getFile() {
        return file;
    }
}
