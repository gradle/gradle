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

package org.gradle.nativeplatform.toolchain.internal.gcc.metadata;

import com.google.common.base.Joiner;
import com.google.common.collect.ImmutableList;
import org.gradle.api.GradleException;
import org.gradle.api.UncheckedIOException;
import org.gradle.internal.FileUtils;
import org.gradle.internal.io.StreamByteBuffer;
import org.gradle.internal.os.OperatingSystem;
import org.gradle.nativeplatform.platform.internal.ArchitectureInternal;
import org.gradle.nativeplatform.platform.internal.Architectures;
import org.gradle.nativeplatform.platform.internal.DefaultNativePlatform;
import org.gradle.nativeplatform.toolchain.internal.SystemLibraries;
import org.gradle.nativeplatform.toolchain.internal.metadata.AbstractMetadataProvider;
import org.gradle.nativeplatform.toolchain.internal.metadata.CompilerType;
import org.gradle.process.internal.ExecAction;
import org.gradle.process.internal.ExecActionFactory;
import org.gradle.util.VersionNumber;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccCompilerType.CLANG;
import static org.gradle.nativeplatform.toolchain.internal.gcc.metadata.GccCompilerType.GCC;

/**
 * Given a File pointing to an (existing) gcc/g++/clang/clang++ binary, extracts the version number and default architecture by running with -dM -E -v and scraping the output.
 */
public class GccMetadataProvider extends AbstractMetadataProvider<GccMetadata> {
    private static final Pattern DEFINE_PATTERN = Pattern.compile("\\s*#define\\s+(\\S+)\\s+(.*)");
    private static final String SYSTEM_INCLUDES_START = "#include <...> search starts here:";
    private static final String SYSTEM_INCLUDES_END = "End of search list.";
    private static final String FRAMEWORK_INCLUDE = " (framework directory)";
    private final GccCompilerType compilerType;

    public static GccMetadataProvider forGcc(ExecActionFactory execActionFactory) {
        return new GccMetadataProvider(execActionFactory, GccCompilerType.GCC);
    }

    public static GccMetadataProvider forClang(ExecActionFactory execActionFactory) {
        return new GccMetadataProvider(execActionFactory, GccCompilerType.CLANG);
    }

    GccMetadataProvider(ExecActionFactory execActionFactory, GccCompilerType compilerType) {
        super(execActionFactory);
        this.compilerType = compilerType;
    }

    @Override
    public CompilerType getCompilerType() {
        return compilerType;
    }

    @Override
    protected List<String> compilerArgs() {
        return ImmutableList.of("-dM", "-E", "-v", "-");
    }

    @Override
    protected GccMetadata parseCompilerOutput(String output, String error, File gccBinary, List<File> path) {
        Map<String, String> defines = parseDefines(output, gccBinary);
        VersionNumber scrapedVersion = determineVersion(defines, gccBinary);
        ArchitectureInternal architecture = determineArchitecture(defines);
        String scrapedVendor = determineVendor(error, scrapedVersion, gccBinary);
        ImmutableList<File> systemIncludes = determineSystemIncludes(defines, path, error);

        return new DefaultGccMetadata(scrapedVersion, scrapedVendor, architecture, systemIncludes);
    }

    private String determineVendor(String error, VersionNumber versionNumber, File gccBinary) {
        BufferedReader reader = new BufferedReader(new StringReader(error));
        String majorMinorOnly = versionNumber.getMajor() + "." + versionNumber.getMinor();
        String line;
        try {
            while ((line = reader.readLine()) != null) {
                if (line.contains(majorMinorOnly)
                    && line.contains(" version ")
                    && line.contains(compilerType.getIdentifier())
                    && !line.contains(" default target ")) {
                    return line;
                }
            }
        } catch (IOException e) {
            // Should not happen reading from a StringReader
            throw new UncheckedIOException(e);
        }
        throw new BrokenResultException(String.format("Could not determine %s metadata: could not find vendor in output of %s.", compilerType.getDescription(), gccBinary));
    }

    private ImmutableList<File> determineSystemIncludes(Map<String, String> defines, List<File> path, String error) {
        File cygpathExe = null;
        boolean isCygwin = defines.containsKey("__CYGWIN__");
        if (isCygwin) {
            cygpathExe = findCygpath(path);
        }

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
                    if (compilerType == CLANG && line.contains(FRAMEWORK_INCLUDE)) {
                        continue;
                    }
                    // Exclude framework directories for GCC - they are added as system search paths but they are actually not
                    if (compilerType == GCC && line.endsWith("/Library/Frameworks")) {
                        continue;
                    }
                    String include = line.trim();
                    if (isCygwin) {
                        include = mapCygwinPath(cygpathExe, include);
                    }
                    builder.add(FileUtils.normalize(new File(include)));
                }
            }
            return builder.build();
        } catch (IOException e) {
            // Should not happen reading from a StringReader
            throw new UncheckedIOException(e);
        }
    }

    private File findCygpath(List<File> path) {
        for (File dir : path) {
            File exe = new File(dir, OperatingSystem.current().getExecutableName("cygpath"));
            if (exe.exists()) {
                return exe;
            }
        }
        File exe = OperatingSystem.current().findInPath("cygpath");
        if (exe != null) {
            return exe;
        }
        throw new IllegalStateException("Could not find 'cygpath' executable in path: " + Joiner.on(File.pathSeparator).join(path));
    }

    private String mapCygwinPath(File cygpathExe, String cygwinPath) {
        ExecAction execAction = getExecActionFactory().newExecAction();
        execAction.setWorkingDir(new File(".").getAbsolutePath());
        execAction.commandLine(cygpathExe.getAbsolutePath(), "-w", cygwinPath);
        StreamByteBuffer buffer = new StreamByteBuffer();
        StreamByteBuffer errorBuffer = new StreamByteBuffer();
        execAction.setStandardOutput(buffer.getOutputStream());
        execAction.setErrorOutput(errorBuffer.getOutputStream());
        execAction.execute().assertNormalExitValue();
        return buffer.readAsString().trim();
    }

    private Map<String, String> parseDefines(String output, File gccBinary) {
        BufferedReader reader = new BufferedReader(new StringReader(output));
        String line;
        Map<String, String> defines = new HashMap<String, String>();
        try {
            while ((line = reader.readLine()) != null) {
                Matcher matcher = DEFINE_PATTERN.matcher(line);
                if (!matcher.matches()) {
                    throw new BrokenResultException(String.format("Could not determine %s metadata: %s produced unexpected output.", compilerType.getDescription(), gccBinary.getName()));
                }
                defines.put(matcher.group(1), matcher.group(2));
            }
        } catch (IOException e) {
            // Should not happen reading from a StringReader
            throw new UncheckedIOException(e);
        }
        if (!defines.containsKey("__GNUC__") && !defines.containsKey("__clang__")) {
            throw new BrokenResultException(String.format("Could not determine %s metadata: %s produced unexpected output.", compilerType.getDescription(), gccBinary.getName()));
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

    private static class DefaultGccMetadata implements GccMetadata, SystemLibraries {
        private final VersionNumber scrapedVersion;
        private final String scrapedVendor;
        private final ArchitectureInternal architecture;
        private final ImmutableList<File> systemIncludes;

        DefaultGccMetadata(VersionNumber scrapedVersion, String scrapedVendor, ArchitectureInternal architecture, ImmutableList<File> systemIncludes) {
            this.scrapedVersion = scrapedVersion;
            this.scrapedVendor = scrapedVendor;
            this.architecture = architecture;
            this.systemIncludes = systemIncludes;
        }

        @Override
        public VersionNumber getVersion() {
            return scrapedVersion;
        }

        @Override
        public SystemLibraries getSystemLibraries() {
            return this;
        }

        @Override
        public List<File> getIncludeDirs() {
            return systemIncludes;
        }

        @Override
        public List<File> getLibDirs() {
            return Collections.emptyList();
        }

        @Override
        public Map<String, String> getPreprocessorMacros() {
            return Collections.emptyMap();
        }

        @Override
        public ArchitectureInternal getDefaultArchitecture() {
            return architecture;
        }

        @Override
        public String getVendor() {
            return scrapedVendor;
        }
    }
}
