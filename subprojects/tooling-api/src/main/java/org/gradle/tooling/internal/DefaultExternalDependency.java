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
package org.gradle.tooling.internal;

import org.gradle.tooling.internal.protocol.ExternalDependencyVersion1;

import java.io.File;
import java.io.Serializable;

public class DefaultExternalDependency implements ExternalDependencyVersion1, Serializable {
    private final File file;
    private final File javadoc;
    private final File source;

    public DefaultExternalDependency(File file, File javadoc, File source) {
        this.file = file;
        this.javadoc = javadoc;
        this.source = source;
    }

    public File getFile() {
        return file;
    }

    public File getJavadoc() {
        return javadoc;
    }

    public File getSource() {
        return source;
    }
}
