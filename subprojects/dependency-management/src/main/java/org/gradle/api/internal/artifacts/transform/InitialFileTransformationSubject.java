/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import com.google.common.collect.ImmutableList;

import java.io.File;
import java.util.List;
import javax.annotation.Nullable;

public class InitialFileTransformationSubject implements TransformationSubject {

    private final File file;

    public InitialFileTransformationSubject(File file) {
        this.file = file;
    }

    @Override
    public List<File> getFiles() {
        return ImmutableList.of(file);
    }

    @Nullable
    @Override
    public Throwable getFailure() {
        return null;
    }

    @Override
    public String getDisplayName() {
        return "file " + file;
    }

    @Override
    public String toString() {
        return getDisplayName();
    }
}
