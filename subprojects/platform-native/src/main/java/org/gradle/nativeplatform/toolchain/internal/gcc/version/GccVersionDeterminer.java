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

package org.gradle.nativeplatform.toolchain.internal.gcc.version;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.Pair;
import org.gradle.internal.io.StreamByteBuffer;
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal;
import org.gradle.nativeplatform.platform.internal.Architectures;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.process.ExecResult;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.util.TreeVisitor;
import org.gradle.util.VersionNumber;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Given a File pointing to an (existing) gcc/g++/clang/clang++ binary, extracts the version number and default architecture by running with -dM -E and scraping the output.
 */
public class GccVersionDeterminer implements CompilerMetaDataProvider {
    private static final Pattern DEFINE_PATTERN = Pattern.compile("\\s*#define\\s+(\\S+)\\s+(.*)");
    private static final String SYSTEM_INCLUDES_START = "#include <...> search starts here:";
    private static final String SYSTEM_INCLUDES_END = "End of search list.";
    private static final String FRAMEWORK_INCLUDE = " (framework directory)";
    private final ExecActionFactory execActionFactory;
    private final CompilerType compilerType;

    public static GccVersionDeterminer forGcc(ExecActionFactory execActionFactory) {
        return new GccVersionDeterminer(execActionFactory, CompilerType.GCC);
    }

    public static GccVersionDeterminer forClang(ExecActionFactory execActionFactory) {
        return new GccVersionDeterminer(execActionFactory, CompilerType.CLANG);
    }

    GccVersionDeterminer(ExecActionFactory execActionFactory, CompilerType compilerType) {
        this.execActionFactory = execActionFactory;
        this.compilerType = compilerType;
    }

    @Override
    public GccVersionResult getGccMetaData(File gccBinary, List<String> args) {
        List<String> allArgs = new ArrayList<String>(args);
        allArgs.add("-dM");
        allArgs.add("-E");
        allArgs.add("-v");
        allArgs.add("-");
        Pair<String, String> transform = transform(gccBinary, allArgs);
        if (transform == null) {
            return new BrokenResult(String.format("Could not determine %s version: failed to execute %s %s.", compilerType.getDescription(), gccBinary.getName(), Joiner.on(' ').join(allArgs)));
        }
        String output = transform.getLeft();
        String error = transform.getRight();
        try {
            return transform(output, error, gccBinary);
        } catch (BrokenResultException e) {
            return new BrokenResult(e.getMessage());
        }
    }

    @Override
    public CompilerType getCompilerType() {
        return compilerType;
    }

    private Pair<String, String> transform(File gccBinary, List<String> args) {
        ExecAction exec = execActionFactory.newExecAction();
        exec.executable(gccBinary.getAbsolutePath());
        exec.setWorkingDir(gccBinary.getParentFile());
        exec.args(args);
        StreamByteBuffer buffer = new StreamByteBuffer();
        StreamByteBuffer errorBuffer = new StreamByteBuffer();
        exec.setStandardOutput(buffer.getOutputStream());
        exec.setErrorOutput(errorBuffer.getOutputStream());
        exec.setIgnoreExitValue(true);
        ExecResult result = exec.execute();

        int exitValue = result.getExitValue();
        if (exitValue == 0) {
            return Pair.of(buffer.readAsString(), errorBuffer.readAsString());
        } else {
            return null;
        }
    }

    private GccVersionResult transform(String output, String error, File gccBinary) {
        Map<String, String> defines = parseDefines(output, gccBinary);
        VersionNumber versionNumber = determineVersion(defines, gccBinary);
        ArchitectureInternal architecture = determineArchitecture(defines);
        ImmutableList<File> systemIncludes = determineSystemIncludes(error);

        return new DefaultGccVersionResult(versionNumber, architecture, systemIncludes);
    }

    private ImmutableList<File> determineSystemIncludes(String error) {
        BufferedReader reader = new BufferedReader(new StringReader(error));
        String line;
        ImmutableList.Builder<File> builder = ImmutableList.builder();
        boolean systemIncludesStarted = false;
        try {
            while ((line = reader.readLine()) != null) {
                if (SYSTEM_INCLUDES_END.equals(line)) {
                    break;
                }
                if (SYSTEM_INCLUDES_START.equals(line)) {
                    systemIncludesStarted = true;
                    continue;
                }
                if (systemIncludesStarted) {
                    // Exclude frameworks for CLang - they need to be handled differently
                    if (compilerType == CompilerType.CLANG && line.contains(FRAMEWORK_INCLUDE)) {
                        continue;
                    }
                    // Exclude framework directories for GCC - they are added as system search paths but they are actually not
                    if (compilerType == CompilerType.GCC && line.endsWith("/Library/Frameworks")) {
                        continue;
                    }
                    builder.add(new File(line.trim()));
                }
            }
            return builder.build();
        } catch (IOException e) {
            // Should not happen reading from a StringReader
            throw new UncheckedIOException(e);
        }
    }

    private Map<String, String> parseDefines(String output, File gccBinary) {
        BufferedReader reader = new BufferedReader(new StringReader(output));
        String line;
        Map<String, String> defines = new HashMap<String, String>();
        try {
            while ((line = reader.readLine()) != null) {
                Matcher matcher = DEFINE_PATTERN.matcher(line);
                if (!matcher.matches()) {
                    throw new BrokenResultException(String.format("Could not determine %s version: %s produced unexpected output.", compilerType.getDescription(), gccBinary.getName()));
                }
                defines.put(matcher.group(1), matcher.group(2));
            }
        } catch (IOException e) {
            // Should not happen reading from a StringReader
            throw new UncheckedIOException(e);
        }
        if (!defines.containsKey("__GNUC__")) {
            throw new BrokenResultException(String.format("Could not determine %s version: %s produced unexpected output.", compilerType.getDescription(), gccBinary.getName()));
        }
        return defines;
    }

    private VersionNumber determineVersion(Map<String, String> defines, File gccBinary) {
        int major;
        int minor;
        int patch;
        switch (compilerType) {
            case CLANG:
                if (!defines.containsKey("__clang__")) {
                    throw new BrokenResultException(String.format("%s appears to be GCC rather than Clang. Treating it as GCC.", gccBinary.getName()));
                }
                major = toInt(defines.get("__clang_major__"));
                minor = toInt(defines.get("__clang_minor__"));
                patch = toInt(defines.get("__clang_patchlevel__"));
                break;
            case GCC:
                if (defines.containsKey("__clang__")) {
                    throw new BrokenResultException(String.format("XCode %s is a wrapper around Clang. Treating it as Clang and not GCC.", gccBinary.getName()));
                }
                major = toInt(defines.get("__GNUC__"));
                minor = toInt(defines.get("__GNUC_MINOR__"));
                patch = toInt(defines.get("__GNUC_PATCHLEVEL__"));
                break;
            default:
                throw new GradleException("Unknown compiler type " + compilerType);
        }
        return new VersionNumber(major, minor, patch, null);
    }

    private static class BrokenResultException extends RuntimeException {
        public BrokenResultException(String message) {
            super(message);
        }
    }

    private ArchitectureInternal determineArchitecture(Map<String, String> defines) {
        boolean i386 = defines.containsKey("__i386__");
        boolean amd64 = defines.containsKey("__amd64__");
        final ArchitectureInternal architecture;
        if (i386) {
            architecture = Architectures.forInput("i386");
        } else if (amd64) {
            architecture = Architectures.forInput("amd64");
        } else {
            architecture = DefaultNativePlatform.getCurrentArchitecture();
        }
        return architecture;
    }

    private int toInt(String value) {
        if (value == null) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static class DefaultGccVersionResult implements GccVersionResult {
        private final VersionNumber scrapedVersion;
        private final ArchitectureInternal architecture;
        private final ImmutableList<File> systemIncludes;

        public DefaultGccVersionResult(VersionNumber scrapedVersion, ArchitectureInternal architecture, ImmutableList<File> systemIncludes) {
            this.scrapedVersion = scrapedVersion;
            this.architecture = architecture;
            this.systemIncludes = systemIncludes;
        }

        @Override
        public VersionNumber getVersion() {
            return scrapedVersion;
        }

        @Override
        public ImmutableList<File> getSystemIncludes() {
            return systemIncludes;
        }

        @Override
        public ArchitectureInternal getDefaultArchitecture() {
            return architecture;
        }

        @Override
        public boolean isAvailable() {
            return true;
        }

        @Override
        public void explain(TreeVisitor<? super String> visitor) {
        }
    }

    public static class BrokenResult implements GccVersionResult {
        private final String message;

        public BrokenResult(String message) {
            this.message = message;
        }

        @Override
        public VersionNumber getVersion() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ImmutableList<File> getSystemIncludes() {
            throw new UnsupportedOperationException();
        }

        @Override
        public ArchitectureInternal getDefaultArchitecture() {
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
}
