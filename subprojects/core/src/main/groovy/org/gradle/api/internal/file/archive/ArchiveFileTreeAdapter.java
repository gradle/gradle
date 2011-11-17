/*
 * Copyright 2011 the original author or authors.
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

package org.gradle.api.internal.file.archive;

import org.gradle.api.file.ArchiveFileTree;
import org.gradle.api.internal.file.archive.decompressors.DecompressorFactory;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.tasks.bundling.Compression;
import org.gradle.api.tasks.bundling.CompressionAware;
import org.gradle.api.tasks.bundling.Decompressor;

/**
 * by Szczepan Faber, created at: 11/16/11
 */
public class ArchiveFileTreeAdapter extends FileTreeAdapter implements ArchiveFileTree {

    private final TarFileTree tarTree;

    public ArchiveFileTreeAdapter(FileTreeAdapter delegate, TarFileTree tarTree) {
        super(delegate.getTree());
        this.tarTree = tarTree;
    }

    public Decompressor getDecompressor() {
        return tarTree.getDecompressor();
    }

    public void setDecompressor(Decompressor decompressor) {
        tarTree.setDecompressor(decompressor);
    }

    public void setCompression(Compression compression) {
        setDecompressor(new DecompressorFactory().decompressor(compression));
    }

    public Compression getCompression() {
        Decompressor d = tarTree.getDecompressor();
        if (d instanceof CompressionAware) {
            return ((CompressionAware) d).getCompression();
        }
        return Compression.NONE;
    }
}