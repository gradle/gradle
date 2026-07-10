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

package org.gradle.testing.testengine.descriptor;

import org.junit.platform.engine.TestSource;
import org.junit.platform.engine.UniqueId;
import org.junit.platform.engine.support.descriptor.AbstractTestDescriptor;
import org.junit.platform.engine.support.descriptor.FileSource;

import java.io.File;
import java.util.Optional;

public final class ResourceBasedTestDescriptor extends AbstractTestDescriptor {
    private final File file;
    private final String name;
    private final boolean dynamic;

    public ResourceBasedTestDescriptor(UniqueId parentId, File file, String name) {
        this(parentId, file, name, false);
    }

    public ResourceBasedTestDescriptor(UniqueId parentId, File file, String name, boolean dynamic) {
        super(parentId.append("testDefinitionFile", file.getName()).append("testDefinition", name), file.getName() + " - " + name);
        this.file = file;
        this.name = name;
        this.dynamic = dynamic;
    }

    @Override
    public Type getType() {
        return dynamic ? Type.CONTAINER_AND_TEST : Type.TEST;
    }

    @Override
    public boolean mayRegisterTests() {
        return dynamic;
    }

    @Override
    public Optional<TestSource> getSource() {
        return Optional.of(FileSource.from(getFile()));
    }

    public File getFile() {
        return file;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "Test[file=" + file.getName() + ", name=" + name + "]";
    }
}
