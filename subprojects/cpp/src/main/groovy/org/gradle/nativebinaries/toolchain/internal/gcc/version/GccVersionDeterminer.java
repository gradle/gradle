/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.nativebinaries.toolchain.internal.gcc.version;

import org.gradle.api.Transformer;
import org.gradle.api.UncheckedIOException;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.util.TreeVisitor;

import java.io.*;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Given a File pointing to an (existing) g++ binary, extracts the version number by running with -v and scraping the output.
 */
public class GccVersionDeterminer implements Transformer<GccVersionResult, File> {
    private static final Pattern DEFINE_PATTERN = Pattern.compile("\\s*#define\\s+(\\S+)\\s+(.*)");

    private final Transformer<String, File> outputProducer;

    public GccVersionDeterminer(ExecActionFactory execActionFactory) {
        this.outputProducer = new GccVersionOutputProducer(execActionFactory);
    }

    public GccVersionResult transform(File gccBinary) {
        String output = outputProducer.transform(gccBinary);
        if (output == null) {
            return new BrokenResult(String.format("Could not determine GCC version: failed to execute %s -v.", gccBinary.getName()));
        }
        return transform(output, gccBinary);
    }

    private GccVersionResult transform(String output, File gccBinary) {
        BufferedReader reader = new BufferedReader(new StringReader(output));
        String line;
        Map<String, String> defines = new HashMap<String, String>();
        try {
            while ((line = reader.readLine()) != null) {
                Matcher matcher = DEFINE_PATTERN.matcher(line);
                if (!matcher.matches()) {
                    return new BrokenResult(String.format("Could not determine GCC version: %s produced unexpected output.", gccBinary.getName()));
                }
                defines.put(matcher.group(1), matcher.group(2));
            }
        } catch (IOException e) {
            // Should not happen reading from a StringReader
            throw new UncheckedIOException(e);
        }
        if (!defines.containsKey("__GNUC__")) {
            return new BrokenResult(String.format("Could not determine GCC version: %s produced unexpected output.", gccBinary.getName()));
        }
        if (defines.containsKey("__clang__")) {
            return new BrokenResult(String.format("XCode %s is a wrapper around Clang. Treating it as Clang and not GCC.", gccBinary.getName()));
        }
        return new DefaultGccVersionResult(defines.get("__GNUC__"));
    }

    private static class DefaultGccVersionResult implements GccVersionResult {
        private final String scrapedVersion;

        public DefaultGccVersionResult(String scrapedVersion) {
            this.scrapedVersion = scrapedVersion;
        }

        public String getVersion() {
            return scrapedVersion;
        }

        public boolean isAvailable() {
            return true;
        }

        public void explain(TreeVisitor<? super String> visitor) {
        }
    }

    static class GccVersionOutputProducer implements Transformer<String, File> {
        
        private final ExecActionFactory execActionFactory;

        GccVersionOutputProducer(ExecActionFactory execActionFactory) {
            this.execActionFactory = execActionFactory;
        }

        public String transform(File gccBinary) {
            ExecAction exec = execActionFactory.newExecAction();
            exec.executable(gccBinary.getAbsolutePath());
            exec.setWorkingDir(gccBinary.getParentFile());
            exec.args("-dM", "-E", "-");
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            exec.setStandardOutput(baos);
            exec.setIgnoreExitValue(true);
            ExecResult result = exec.execute();

            int exitValue = result.getExitValue();
            if (exitValue == 0) {
                return new String(baos.toByteArray());
            } else {
                return null;
            }
        }
    }

    private static class BrokenResult implements GccVersionResult {
        private final String message;

        private BrokenResult(String message) {
            this.message = message;
        }

        public String getVersion() {
            throw new UnsupportedOperationException();
        }

        public boolean isAvailable() {
            return false;
        }

        public void explain(TreeVisitor<? super String> visitor) {
            visitor.node(message);
        }
    }
}
