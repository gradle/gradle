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

import java.io.IOException;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class ZipPacker implements Packer {

    private final byte[] buffer;

    public ZipPacker(int bufferSizeInKBytes) {
        this.buffer = new byte[bufferSizeInKBytes];
    }

    @Override
    public void pack(List<DataSource> inputs, DataTarget output) throws IOException {
        ZipOutputStream zipOutput = new ZipOutputStream(output.openOutput());
        for (DataSource input : inputs) {
            ZipEntry entry = new ZipEntry(input.getName());
            entry.setSize(input.getLength());
            zipOutput.putNextEntry(entry);
            PackerUtils.packEntry(input, zipOutput, buffer);
            zipOutput.closeEntry();
        }
        zipOutput.close();
    }

    @Override
    public void unpack(DataSource input, DataTargetFactory targetFactory) throws IOException {
        ZipInputStream zipInput = new ZipInputStream(input.openInput());
        while (true) {
            ZipEntry entry = zipInput.getNextEntry();
            if (entry == null) {
                break;
            }
            PackerUtils.unpackEntry(entry.getName(), zipInput, buffer, targetFactory);
        }
        zipInput.close();
    }
}
