/*
 * Copyright 2026 the original author or authors.
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

/**
 * A container descriptor representing a test definition file (e.g., a {@code .rbt} file).
 * Groups all individual test definitions from the same file.
 */
public final class TestDefinitionFileDescriptor extends AbstractTestDescriptor {
    private final File file;

    public TestDefinitionFileDescriptor(UniqueId parentId, File file) {
        super(parentId.append("testDefinitionFile", file.getAbsolutePath()), file.getName());
        this.file = file;
    }

    @Override
    public Type getType() {
        return Type.CONTAINER;
    }

    @Override
    public Optional<TestSource> getSource() {
        return Optional.of(FileSource.from(file));
    }

    public File getFile() {
        return file;
    }
}
