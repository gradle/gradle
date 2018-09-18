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

import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;

import java.io.IOException;
import java.util.List;

public class CommonsTarPacker implements Packer {

    private final byte[] buffer;

    public CommonsTarPacker(int bufferSizeInKBytes) {
        this.buffer = new byte[bufferSizeInKBytes * 1024];
    }

    @Override
    public void pack(List<DataSource> inputs, DataTarget output) throws IOException {
        TarArchiveOutputStream tarOutput = new TarArchiveOutputStream(output.openOutput());
        for (DataSource input : inputs) {
            TarArchiveEntry entry = new TarArchiveEntry(input.getName());
            entry.setSize(input.getLength());
            tarOutput.putArchiveEntry(entry);
            PackerUtils.packEntry(input, tarOutput, buffer);
            tarOutput.closeArchiveEntry();
        }
        tarOutput.close();
    }

    @Override
    public void unpack(DataSource input, DataTargetFactory targetFactory) throws IOException {
        TarArchiveInputStream tarInput = new TarArchiveInputStream(input.openInput());
        while (true) {
            TarArchiveEntry entry = tarInput.getNextTarEntry();
            if (entry == null) {
                break;
            }
            PackerUtils.unpackEntry(entry.getName(), tarInput, buffer, targetFactory);
        }
        tarInput.close();
    }
}
