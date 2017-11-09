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

package org.gradle.nativeplatform.toolchain.internal.swift;

import com.google.common.collect.ImmutableList;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.io.StreamByteBuffer;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.util.TreeVisitor;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.List;

public class SwiftcVersionDeterminer implements CompilerMetaDataProvider {

    private final ExecActionFactory execActionFactory;

    public SwiftcVersionDeterminer(ExecActionFactory execActionFactory) {
        this.execActionFactory = execActionFactory;
    }

    @Override
    public SwiftcVersionResult getSwiftcMetaData(File swiftc) {
        String result = execute(swiftc, ImmutableList.of("--version"));
        if (result == null) {
            return new BrokenResult(String.format("%s --version exited with non-zero exit status", swiftc.getName()));
        }
        return parseOutput(result, swiftc);
    }

    private SwiftcVersionResult parseOutput(String stdout, File swiftc) {
        BufferedReader reader = new BufferedReader(new StringReader(stdout));
        try {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.contains("Swift version")) {
                    return new DefaultSwiftcVersionResult(line);
                }
            }
            return new BrokenResult(String.format("Could not parse output of %s", swiftc.getName()));
        } catch (IOException e) {
            // Should not happen when reading from a StringReader
            throw new UncheckedIOException(e);
        }
    }

    private String execute(File swiftc, List<String> args) {
        ExecAction exec = execActionFactory.newExecAction();
        exec.executable(swiftc.getAbsolutePath());
        exec.setWorkingDir(swiftc.getParentFile());
        exec.args(args);
        StreamByteBuffer buffer = new StreamByteBuffer();
        exec.setStandardOutput(buffer.getOutputStream());
        exec.setIgnoreExitValue(true);
        ExecResult result = exec.execute();

        int exitValue = result.getExitValue();
        if (exitValue == 0) {
            return buffer.readAsString();
        } else {
            return null;
        }
    }

    private class BrokenResult implements SwiftcVersionResult {

        private final String message;

        public BrokenResult(String message) {
            this.message = message;
        }

        @Override
        public String getVersionString() {
            throw new UnsupportedOperationException();
        }

        @Override
        public boolean isAvailable() {
            return false;
        }

        @Override
        public void explain(TreeVisitor<? super String> visitor) {
            visitor.node(message);
        }
    }

    public class DefaultSwiftcVersionResult implements SwiftcVersionResult {
        private final String versionString;

        public DefaultSwiftcVersionResult(String versionString) {
            this.versionString = versionString;
        }

        public String getVersionString() {
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
