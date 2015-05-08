/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.internal.resources;

import com.google.common.io.CharSource;
import org.gradle.api.Task;
import org.gradle.api.file.FileCollection;
import org.gradle.api.resources.TextResource;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.internal.UncheckedException;

import java.io.File;
import java.io.IOException;
import java.io.Reader;
import java.util.Collections;
import java.util.Set;

public class CharSourceBackedTextResource implements TextResource {

    private final CharSource charSource;

    public CharSourceBackedTextResource(CharSource charSource) {
        this.charSource = charSource;
    }

    @Override
    public String asString() {
        try {
            return charSource.read();
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public Reader asReader() {
        try {
            return charSource.openStream();
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }

    @Override
    public File asFile(String charset) {
        throw new UnsupportedOperationException("Cannot create file for char source " + charSource);
    }

    @Override
    public File asFile() {
        throw new UnsupportedOperationException("Cannot create file for char source " + charSource);
    }

    @Override
    public Object getInputProperties() {
        return null;
    }

    @Override
    public FileCollection getInputFiles() {
        return null;
    }

    @Override
    public TaskDependency getBuildDependencies() {
        return new TaskDependency() {
            @Override
            public Set<? extends Task> getDependencies(Task task) {
                return Collections.emptySet();
            }
        };
    }
}
