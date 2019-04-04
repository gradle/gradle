/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.api.file.FileCollection;
import org.gradle.api.file.FileTree;
import org.gradle.api.internal.file.DefaultCompositeFileTree;
import org.gradle.api.internal.file.FileCollectionFactory;
import org.gradle.api.internal.file.FileCollectionInternal;
import org.gradle.api.internal.file.FileCollectionLeafVisitor;
import org.gradle.api.internal.file.FileTreeInternal;
import org.gradle.api.internal.file.collections.DirectoryFileTreeFactory;
import org.gradle.api.internal.file.collections.FileTreeAdapter;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.internal.serialize.BaseSerializerFactory;
import org.gradle.internal.serialize.Decoder;
import org.gradle.internal.serialize.Encoder;
import org.gradle.internal.serialize.Serializer;
import org.gradle.internal.serialize.SetSerializer;

import java.io.EOFException;
import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

class PropertyValueSerializer implements Serializer<Object> {
    private final Serializer<Set<File>> fileSetSerializer = new SetSerializer<File>(BaseSerializerFactory.FILE_SERIALIZER);
    private final Serializer<String> stringSerializer = BaseSerializerFactory.STRING_SERIALIZER;
    private final DirectoryFileTreeFactory directoryFileTreeFactory;
    private final FileCollectionFactory fileCollectionFactory;
    private final Serializer<FileTreeInternal> fileTreeSerializer = new Serializer<FileTreeInternal>() {
        @Override
        public FileTreeInternal read(Decoder decoder) throws Exception {
            Set<File> roots = fileSetSerializer.read(decoder);
            List<FileTreeInternal> fileTrees = new ArrayList<FileTreeInternal>(roots.size());
            for (File root : roots) {
                fileTrees.add(new FileTreeAdapter(directoryFileTreeFactory.create(root)));
            }
            return new DefaultCompositeFileTree(fileTrees);
        }

        @Override
        public void write(Encoder encoder, FileTreeInternal value) throws Exception {
            FileTreeVisitor visitor = new FileTreeVisitor();
            value.visitLeafCollections(visitor);
            fileSetSerializer.write(encoder, visitor.roots);
        }
    };
    private static final byte NULL_VALUE = 0;
    private static final byte STRING_TYPE = 1;
    private static final byte FILE_TREE_TYPE = 2;
    private static final byte FILE_TYPE = 3;
    private static final byte FILE_COLLECTION_TYPE = 4;

    public PropertyValueSerializer(DirectoryFileTreeFactory directoryFileTreeFactory, FileCollectionFactory fileCollectionFactory) {
        this.directoryFileTreeFactory = directoryFileTreeFactory;
        this.fileCollectionFactory = fileCollectionFactory;
    }

    @Override
    public Object read(Decoder decoder) throws EOFException, Exception {
        byte tag = decoder.readByte();
        switch (tag) {
            case NULL_VALUE:
                return null;
            case STRING_TYPE:
                return stringSerializer.read(decoder);
            case FILE_TREE_TYPE:
                return fileTreeSerializer.read(decoder);
            case FILE_TYPE:
                return BaseSerializerFactory.FILE_SERIALIZER.read(decoder);
            case FILE_COLLECTION_TYPE:
                return fileCollectionFactory.fixed(fileSetSerializer.read(decoder));
            default:
                throw new UnsupportedOperationException();
        }
    }

    @Override
    public void write(Encoder encoder, Object value) throws Exception {
        if (value == null) {
            encoder.writeByte(NULL_VALUE);
        } else if (value instanceof String) {
            encoder.writeByte(STRING_TYPE);
            stringSerializer.write(encoder, (String) value);
        } else if (value instanceof FileTreeInternal) {
            encoder.writeByte(FILE_TREE_TYPE);
            fileTreeSerializer.write(encoder, (FileTreeInternal) value);
        } else if (value instanceof File) {
            encoder.writeByte(FILE_TYPE);
            BaseSerializerFactory.FILE_SERIALIZER.write(encoder, (File) value);
        } else if (value instanceof FileCollection) {
            encoder.writeByte(FILE_COLLECTION_TYPE);
            fileSetSerializer.write(encoder, ((FileCollection) value).getFiles());
        } else {
            throw new UnsupportedOperationException();
        }
    }

    public boolean canWrite(Class<?> type) {
        return type.equals(String.class) || type.equals(FileTree.class) || type.equals(File.class) || type.equals(FileCollection.class);
    }

    private static class FileTreeVisitor implements FileCollectionLeafVisitor {
        Set<File> roots = new LinkedHashSet<File>();

        @Override
        public void visitCollection(FileCollectionInternal fileCollection) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void visitGenericFileTree(FileTreeInternal fileTree) {
            throw new UnsupportedOperationException();
        }

        @Override
        public void visitFileTree(File root, PatternSet patterns) {
            roots.add(root);
        }
    }
}
