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

package org.gradle.nativeplatform.toolchain.internal.swift.metadata;

import com.google.common.collect.ImmutableList;
import org.gradle.api.UncheckedIOException;
import org.gradle.nativeplatform.toolchain.internal.metadata.AbstractMetadataProvider;
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerType;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.util.TreeVisitor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

public class SwiftcMetadataProvider extends AbstractMetadataProvider<SwiftcMetadata> {

    private static final CompilerType SWIFTC_COMPILER_TYPE = new CompilerType() {
        @Override
        public String getIdentifier() {
            return "swiftc";
        }

        @Override
        public String getDescription() {
            return "SwiftC";
        }
    };

    public SwiftcMetadataProvider(ExecActionFactory execActionFactory) {
        super(execActionFactory);
    }

    @Override
    protected List<String> compilerArgs() {
        return ImmutableList.of("--version");
    }

    @Override
    protected SwiftcMetadata brokenMetadata(String message) {
        return new BrokenMetadata(message);
    }

    @Override
    public CompilerType getCompilerType() {
        return SWIFTC_COMPILER_TYPE;
    }

    @Override
    protected SwiftcMetadata parseCompilerOutput(String stdout, String stderr, File swiftc) {
        BufferedReader reader = new BufferedReader(new StringReader(stdout));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Swift version")) {
                    return new DefaultSwiftcMetadata(line);
                }
            }
            return new BrokenMetadata(String.format("Could not determine %s metadata: %s produced unexpected output.", getCompilerType().getDescription(), swiftc.getName()));
        } catch (IOException e) {
            // Should not happen when reading from a StringReader
            throw new UncheckedIOException(e);
        }
    }

    private class BrokenMetadata extends AbstractBrokenMetadata implements SwiftcMetadata {

        public BrokenMetadata(String message) {
            super(message);
        }

    }

    private class DefaultSwiftcMetadata implements SwiftcMetadata {
        private final String versionString;

        public DefaultSwiftcMetadata(String versionString) {
            this.versionString = versionString;
        }

        public String getVendor() {
            return versionString;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void explain(TreeVisitor<? super String> visitor) {
        }
    }

}
