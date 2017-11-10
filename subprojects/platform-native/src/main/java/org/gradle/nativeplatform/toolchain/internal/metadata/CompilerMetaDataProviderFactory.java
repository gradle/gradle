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

package org.gradle.nativeplatform.toolchain.internal.metadata;

import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccMetadata;
import org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccMetadataProvider;
import org.gradle.nativeplatform.toolchain.internal.swift.metadata.SwiftcMetadata;
import org.gradle.nativeplatform.toolchain.internal.swift.metadata.SwiftcMetadataProvider;
import org.gradle.process.internal.ExecActionFactory;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CompilerMetaDataProviderFactory {
    private final CachingCompilerMetaDataProvider<GccMetadata> gcc;
    private final CachingCompilerMetaDataProvider<GccMetadata> clang;
    private final CachingCompilerMetaDataProvider<SwiftcMetadata> swiftc;

    public CompilerMetaDataProviderFactory(ExecActionFactory execActionFactory) {
        gcc = new CachingCompilerMetaDataProvider<GccMetadata>(GccMetadataProvider.forGcc(execActionFactory));
        clang = new CachingCompilerMetaDataProvider<GccMetadata>(GccMetadataProvider.forClang(execActionFactory));
        swiftc = new CachingCompilerMetaDataProvider<SwiftcMetadata>(new SwiftcMetadataProvider(execActionFactory));
    }

    public CompilerMetaDataProvider<GccMetadata> gcc() {
        return gcc;
    }

    public CompilerMetaDataProvider<GccMetadata> clang() {
        return clang;
    }

    public CompilerMetaDataProvider<SwiftcMetadata> swiftc() {
        return swiftc;
    }

    private static class CachingCompilerMetaDataProvider<T extends CompilerMetadata> implements CompilerMetaDataProvider<T> {
        private final CompilerMetaDataProvider<T> delegate;
        private final Map<Key, T> resultMap = new HashMap<Key, T>();

        private CachingCompilerMetaDataProvider(CompilerMetaDataProvider<T> delegate) {
            this.delegate = delegate;
        }

        @Override
        public T getCompilerMetaData(File binary, List<String> additionalArgs) {
            Key key = new Key(binary, additionalArgs);
            T result = resultMap.get(key);
            if (result == null) {
                result = delegate.getCompilerMetaData(binary, additionalArgs);
                resultMap.put(key, result);
            }
            return result;
        }

        @Override
        public CompilerType getCompilerType() {
            return delegate.getCompilerType();
        }
    }

    private static class Key {
        final File gccBinary;
        final List<String> args;

        private Key(File gccBinary, List<String> args) {
            this.gccBinary = gccBinary;
            this.args = args;
        }

        @Override
        public boolean equals(Object obj) {
            Key other = (Key) obj;
            return other.gccBinary.equals(gccBinary) && other.args.equals(args);
        }

        @Override
        public int hashCode() {
            return gccBinary.hashCode() ^ args.hashCode();
        }
    }
}
