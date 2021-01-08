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

import org.kamranzafar.jtar.TarEntry;
import org.kamranzafar.jtar.TarHeader;
import org.kamranzafar.jtar.TarInputStream;
import org.kamranzafar.jtar.TarOutputStream;

import java.io.IOException;
import java.util.List;

public class JTarPacker implements Packer {

    private final byte[] buffer;

    public JTarPacker(int bufferSizeInKBytes) {
        this.buffer = new byte[bufferSizeInKBytes * 1024];
    }

    @Override
    public void pack(List<DataSource> inputs, DataTarget output) throws IOException {
        TarOutputStream tarOutput = new TarOutputStream(output.openOutput());
        for (DataSource input : inputs) {
            @SuppressWarnings("OctalInteger")
            TarHeader header = TarHeader.createHeader(input.getName(), input.getLength(), 0, false, 0644);
            TarEntry entry = new TarEntry(header);
            tarOutput.putNextEntry(entry);
            PackerUtils.packEntry(input, tarOutput, buffer);
        }
        tarOutput.close();
    }

    @Override
    public void unpack(DataSource input, DataTargetFactory targetFactory) throws IOException {
        TarInputStream tarInput = new TarInputStream(input.openInput());
        while (true) {
            TarEntry entry = tarInput.getNextEntry();
            if (entry == null) {
                break;
            }
            PackerUtils.unpackEntry(entry.getName(), tarInput, buffer, targetFactory);
        }
        tarInput.close();
    }
}
