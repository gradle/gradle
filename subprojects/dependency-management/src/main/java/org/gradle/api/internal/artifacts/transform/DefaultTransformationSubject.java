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

import java.io.File;
import java.util.List;

public class DefaultTransformationSubject implements TransformationSubject{
    private final List<File> files;
    private final Throwable failure;

    public DefaultTransformationSubject(List<File> files) {
        this.files = files;
        this.failure = null;
    }

    public DefaultTransformationSubject(Throwable failure) {
        this.failure = failure;
        this.files = null;
    }

    @Override
    public List<File> getFiles() {
        return files;
    }

    @Override
    public Throwable getFailure() {
        return failure;
    }
}
