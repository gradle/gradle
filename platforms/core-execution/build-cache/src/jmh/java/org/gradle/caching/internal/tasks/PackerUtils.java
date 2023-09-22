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

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class PackerUtils {
    public static void packEntry(DataSource entry, OutputStream outputStream, byte[] buffer) throws IOException {
        InputStream inputStream = entry.openInput();
        try {
            IOUtils.copyLarge(inputStream, outputStream, buffer);
        } finally {
            inputStream.close();
        }
    }

    public static void unpackEntry(String name, InputStream inputStream, byte[] buffer, DataTargetFactory targetFactory) throws IOException {
        DataTarget target = targetFactory.createDataTarget(name);
        OutputStream outputStream = target.openOutput();
        try {
            IOUtils.copyLarge(inputStream, outputStream, buffer);
        } finally {
            outputStream.close();
        }
    }
}
