/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.internal.tasks;

import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Threads;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Path;

@Fork(1)
@Threads(4)
@SuppressWarnings("Since15")
public class BufferingFileBasedTaskOutputPackagingBenchmark extends NonBufferingFileBasedTaskOutputPackagingBenchmark {
    @Override
    protected InputStream openInput(Path path) throws IOException {
        return new BufferedInputStream(super.openInput(path));
    }

    @Override
    protected OutputStream openOutput(Path path) throws IOException {
        return new BufferedOutputStream(super.openOutput(path));
    }
}
