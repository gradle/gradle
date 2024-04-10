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
package org.gradle.launcher.daemon.context;

import org.gradle.api.internal.specs.ExplainingSpec;
import org.gradle.internal.jvm.Jvm;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.MatchResult;
import java.util.regex.Pattern;

public class DaemonCompatibilitySpec implements ExplainingSpec<DaemonContext> {

    private final DaemonRequestContext request;
    private final DaemonOpts parsedArgs;

    public DaemonCompatibilitySpec(DaemonRequestContext request) {
        this.request = request;
        this.parsedArgs = parseDaemonOpts(request.getDaemonOpts());
    }

    @Override
    public boolean isSatisfiedBy(DaemonContext potentialContext) {
        return whyUnsatisfied(potentialContext) == null;
    }

    @Override
    public String whyUnsatisfied(DaemonContext context) {
        if (!jvmCompatible(context)) {
            return "JVM is incompatible.\n" + description(context);
        } else if (!daemonOptsMatch(context)) {
            return "At least one daemon option is different.\n" + description(context);
        } else if (!priorityMatches(context)) {
            return "Process priority is different.\n" + description(context);
        } else if (!agentStatusMatches(context)) {
            return "Agent status is different.\n" + description(context);
        } else if (!nativeServicesModeMatches(context)) {
            return "Native services mode is different.\n" + description(context);
        }
        return null;
    }

    private String description(DaemonContext context) {
        return "Wanted: " + request + "\n"
            + "Actual: " + context + "\n";
    }

    private boolean jvmCompatible(DaemonContext potentialContext) {
        if (request.getJvmCriteria() != null) {
            return request.getJvmCriteria().isCompatibleWith(potentialContext.getJavaVersion());
        } else {
            try {
                File potentialJavaHome = potentialContext.getJavaHome();
                if (potentialJavaHome.exists()) {
                    File potentialJava = Jvm.forHome(potentialJavaHome).getJavaExecutable();
                    File desiredJava = Jvm.forHome(request.getJavaHome()).getJavaExecutable();
                    return Files.isSameFile(potentialJava.toPath(), desiredJava.toPath());
                }
            } catch (IOException e) {
                // ignore
            }
        }
        return false;
    }

    private boolean daemonOptsMatch(DaemonContext context) {
        return parsedArgs.isCompatibleWith(parseDaemonOpts(context.getDaemonOpts()));
    }

    private DaemonOpts parseDaemonOpts(Collection<String> args) {
        long minMemory = 0L;
        long maxMemory = Long.MAX_VALUE;
        long metaspace = Long.MAX_VALUE;
        final Set<String> unknownArgs = new LinkedHashSet<>();
        final Set<String> ignorableArgs = new LinkedHashSet<>();
        final Set<String> systemProperties = new LinkedHashSet<>();

        for (String arg : args) {
            if (arg.startsWith("-Xms")) {
                minMemory = parseMemorySpecification(arg.substring("-Xms".length()));
            } else if (arg.startsWith("-Xmx")) {
                maxMemory = parseMemorySpecification(arg.substring("-Xmx".length()));
            } else if (arg.startsWith("-XX:MaxMetaspaceSize")) {
                metaspace = parseMemorySpecification(arg.substring("-XX:MaxMetaspaceSize".length()));
            } else if (arg.startsWith("-D")) {
                systemProperties.add(arg);
            } else if (arg.startsWith("-ea") || arg.startsWith("-XX") || arg.startsWith("-agentlib:jdwp")) {
                ignorableArgs.add(arg);
            } else {
                unknownArgs.add(arg);
            }
        }

        return new DaemonOpts(minMemory, maxMemory, metaspace, unknownArgs, ignorableArgs, systemProperties);
    }

    private final static Pattern MEMORY_PATTERN = Pattern.compile("([0-9]+)([kKmMgGtT])");

    @SuppressWarnings("fallthrough")
    private long parseMemorySpecification(String s) {
        Scanner scanner = new Scanner(s);
        scanner.findInLine(MEMORY_PATTERN);
        MatchResult result = scanner.match();
        if (result.groupCount() == 1) {
            return Long.parseLong(result.group(1));
        } else if (result.groupCount() == 2) {
            long num = Long.parseLong(result.group(1));

            switch(result.group(2)) {
                case "t":
                case "T":
                    num = num*1000;
                case "g":
                case "G":
                    num = num*1000;
                case "m":
                case "M":
                    num = num*1000;
                case "k":
                case "K":
                    num = num*1000;
            }
            return num;
        }

        return 0;
    }

    private final static class DaemonOpts {
        private final long minMemory;
        private final long maxMemory;
        private final long metaspace;
        private final Set<String> unknownArgs;
        private final Set<String> ignorableArgs;
        private final Set<String> systemProperties;

        private DaemonOpts(long minMemory, long maxMemory, long metaspace, Set<String> unknownArgs, Set<String> ignorableArgs, Set<String> systemProperties) {
            this.minMemory = minMemory;
            this.maxMemory = maxMemory;
            this.metaspace = metaspace;
            this.unknownArgs = unknownArgs;
            this.ignorableArgs = ignorableArgs;
            this.systemProperties = systemProperties;
        }

        /**
         * Checks if the given DaemonOpts is compatible with this one.
         *
         * Compatibility means that this DaemonOpts "fits inside" the other memory wise, all unknown arguments are equal and all system properties are equal.
         *
         * We allow certain arguments to be ignored if they do not affect the daemon's behavior.
         */
        public boolean isCompatibleWith(DaemonOpts other) {
            return minMemory <= other.minMemory
                && maxMemory <= other.maxMemory
                && metaspace <= other.metaspace

                && unknownArgs.containsAll(other.unknownArgs)
                && unknownArgs.size() == other.unknownArgs.size()

                && other.ignorableArgs.containsAll(ignorableArgs)

                && systemProperties.containsAll(other.systemProperties)
                && systemProperties.size() == other.systemProperties.size();
        }
    }

    private boolean priorityMatches(DaemonContext context) {
        return request.getPriority() == context.getPriority();
    }

    private boolean agentStatusMatches(DaemonContext context) {
        return request.shouldApplyInstrumentationAgent() == context.shouldApplyInstrumentationAgent();
    }

    private boolean nativeServicesModeMatches(DaemonContext context) {
        return request.getNativeServicesMode() == context.getNativeServicesMode();
    }

    @Override
    public String toString() {
        return request.toString();
    }
}
