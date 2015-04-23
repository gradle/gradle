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

package org.gradle.internal.filewatch.jdk7;

import org.gradle.internal.concurrent.Stoppable;

import java.io.IOException;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

interface WatchStrategy {
    Stoppable watchSingleDirectory(Path path) throws IOException;
    boolean pollChanges(long timeout, TimeUnit unit, WatchHandler watchHandler) throws InterruptedException;
    void close();
}
