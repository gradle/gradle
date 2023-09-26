/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.caching.local.internal;

import org.gradle.internal.hash.HashCode;
import org.gradle.internal.io.IoConsumer;

import java.io.Closeable;
import java.io.File;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.function.Consumer;

public interface LocalBuildCache extends Closeable {
    boolean load(HashCode key, IoConsumer<InputStream> reader);

    void loadLocally(HashCode key, Consumer<? super File> reader);

    void store(HashCode key, IoConsumer<OutputStream> result);

    void storeLocally(HashCode key, File file);
}
