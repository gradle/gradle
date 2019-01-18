/*
 * Copyright 2018 the original author or authors.
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
package org.gradle.performance.fixture;

import com.google.common.base.CharMatcher;
import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Lists;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.IoActions;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.Arrays.asList;
import static java.util.Collections.singletonList;

/**
 * Simplifies stacks to make flame graphs more readable.
 */
public class FlameGraphSanitizer {
    private static final Splitter STACKTRACE_SPLITTER = Splitter.on(";").omitEmptyStrings();
    private static final Joiner STACKTRACE_JOINER = Joiner.on(";");

    public static final SanitizeFunction COLLAPSE_BUILD_SCRIPTS = new ReplaceRegex(
        ImmutableMap.of(
            Pattern.compile("build_[a-z0-9]+"), "build script",
            Pattern.compile("settings_[a-z0-9]+"), "settings script"
        )
    );

    public static final SanitizeFunction COLLAPSE_GRADLE_INFRASTRUCTURE = new CompositeSanitizeFunction(
        new ChopPrefix("loadSettings"),
        new ChopPrefix("configureBuild"),
        new ChopPrefix("constructTaskGraph"),
        new ChopPrefix("executeTasks"),
        new ChopPrefix("org.gradle.api.internal.tasks.execution"),
        new ReplaceContainment(singletonList("org.gradle.api.internal.tasks.execution"), "task execution"),
        new ReplaceContainment(asList("DynamicObject", "Closure.call", "MetaClass", "MetaMethod", "CallSite", "ConfigureDelegate", "Method.invoke", "MethodAccessor", "Proxy", "ConfigureUtil", "Script.invoke", "ClosureBackedAction", "getProperty("), "dynamic invocation"),
        new ReplaceContainment(asList("BuildOperation", "PluginManager", "ObjectConfigurationAction", "PluginTarget", "PluginAware", "Script.apply", "ScriptPlugin", "ScriptTarget", "ScriptRunner", "ProjectEvaluator", "Project.evaluate"), "Gradle infrastructure")
    );

    public static final SanitizeFunction SIMPLE_NAMES = new ToSimpleName();

    private final SanitizeFunction sanitizeFunction;

    public FlameGraphSanitizer(SanitizeFunction... sanitizeFunctions) {
        ImmutableList<SanitizeFunction> functions = ImmutableList.<SanitizeFunction>builder()
            .addAll(Arrays.asList(sanitizeFunctions))
            .add(new CollapseDuplicateFrames())
            .build();
        this.sanitizeFunction = new CompositeSanitizeFunction(functions);
    }

    public void sanitize(final File in, File out) {
        out.getParentFile().mkdirs();
        IoActions.writeTextFile(out, new ErroringAction<BufferedWriter>() {
            @Override
            protected void doExecute(BufferedWriter writer) throws Exception {
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(in)));
                String line;
                StringBuilder sb = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    int endOfStack = line.lastIndexOf(" ");
                    if (endOfStack <= 0) {
                        continue;
                    }
                    String stackTrace = line.substring(0, endOfStack);
                    String invocationCount = line.substring(endOfStack + 1);
                    List<String> stackTraceElements = STACKTRACE_SPLITTER.splitToList(stackTrace);
                    List<String> sanitizedStackElements = sanitizeFunction.map(stackTraceElements);
                    if (!sanitizedStackElements.isEmpty()) {
                        sb.setLength(0);
                        STACKTRACE_JOINER.appendTo(sb, sanitizedStackElements);
                        sb.append(" ");
                        sb.append(invocationCount);
                        sb.append("\n");
                        writer.write(sb.toString());
                    }
                }
            }
        });
    }

    public interface SanitizeFunction {
        List<String> map(List<String> stack);
    }

    private static class CompositeSanitizeFunction implements SanitizeFunction {

        private final List<SanitizeFunction> sanitizeFunctions;

        private CompositeSanitizeFunction(SanitizeFunction... sanitizeFunctions) {
            this(Arrays.asList(sanitizeFunctions));
        }

        private CompositeSanitizeFunction(Collection<SanitizeFunction> sanitizeFunctions) {
            this.sanitizeFunctions = ImmutableList.copyOf(sanitizeFunctions);
        }

        @Override
        public List<String> map(List<String> stack) {
            List<String> result = stack;
            for (SanitizeFunction sanitizeFunction : sanitizeFunctions) {
                result = sanitizeFunction.map(result);
            }
            return result;
        }
    }

    private static abstract class FrameWiseSanitizeFunction implements SanitizeFunction {
        @Override
        public final List<String> map(List<String> stack) {
            List<String> result = Lists.newArrayListWithCapacity(stack.size());
            for (String frame : stack) {
                result.add(mapFrame(frame));
            }
            return result;
        }

        protected abstract String mapFrame(String frame);
    }

    private static class ReplaceContainment extends FrameWiseSanitizeFunction {
        private final Collection<String> keyWords;
        private final String replacement;

        private ReplaceContainment(Collection<String> keyWords, String replacement) {
            this.keyWords = keyWords;
            this.replacement = replacement;
        }

        @Override
        protected String mapFrame(String frame) {
            for (String keyWord : keyWords) {
                if (frame.contains(keyWord)) {
                    return replacement;
                }
            }
            return frame;
        }
    }

    private static class ReplaceRegex extends FrameWiseSanitizeFunction {
        private final Map<Pattern, String> replacements;

        private ReplaceRegex(Map<Pattern, String> replacements) {
            this.replacements = replacements;
        }

        @Override
        protected String mapFrame(String frame) {
            for (Map.Entry<Pattern, String> replacement : replacements.entrySet()) {
                Matcher matcher = replacement.getKey().matcher(frame);
                String value = replacement.getValue();
                StringBuffer sb = new StringBuffer();
                while (matcher.find()) {
                    matcher.appendReplacement(sb, value);
                }
                matcher.appendTail(sb);
                if (sb.length() > 0) {
                    frame = sb.toString();
                }
            }
            return frame;
        }
    }

    private static class CollapseDuplicateFrames implements SanitizeFunction {
        @Override
        public List<String> map(List<String> stack) {
            List<String> result = Lists.newArrayList(stack);
            ListIterator<String> iterator = result.listIterator();
            String previous = null;
            while (iterator.hasNext()) {
                String next = iterator.next();
                if (next.equals(previous)) {
                    iterator.remove();
                }
                previous = next;
            }
            return result;
        }
    }

    private static class ChopPrefix implements SanitizeFunction {
        private final String stopToken;

        private ChopPrefix(String stopToken) {
            this.stopToken = stopToken;
        }

        @Override
        public List<String> map(List<String> stack) {
            for (int i = 0; i < stack.size(); i++) {
                String frame = stack.get(i);
                if (frame.contains(stopToken)) {
                    return stack.subList(i, stack.size());
                }
            }
            return stack;
        }
    }

    private static class ToSimpleName extends FrameWiseSanitizeFunction {

        @Override
        protected String mapFrame(String frame) {
            int firstUpper = CharMatcher.javaUpperCase().indexIn(frame);
            return frame.substring(Math.max(firstUpper, 0));
        }
    }
}
