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

package org.gradle.caching.internal.controller;

import com.google.common.io.CountingOutputStream;
import org.gradle.caching.BuildCacheEntryWriter;

import java.io.IOException;
import java.io.OutputStream;

class CommandBackedEntryWriter implements BuildCacheEntryWriter {

    private final BuildCacheStoreCommand command;

    BuildCacheStoreCommand.Result result;
    long bytes;

    CommandBackedEntryWriter(BuildCacheStoreCommand command) {
        this.command = command;
    }

    @Override
    public void writeTo(OutputStream output) throws IOException {
        // TODO: reenable after fixing HTTP service to not send twice when using URL credentials
//        if (result != null) {
//            throw new IllegalStateException("Build cache entry has already been written");
//        }

        CountingOutputStream countingOutputStream = new CountingOutputStream(output);
        result = command.store(countingOutputStream);
        bytes = countingOutputStream.getCount();
    }

}
