/*
 * Copyright 2016 the original author or authors.
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

import com.google.common.base.Joiner;
import com.google.common.base.Splitter;
import org.gradle.internal.ErroringAction;
import org.gradle.internal.IoActions;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class FlameGraphSanitizer {
    private static final Splitter LINE_SPLITTER = Splitter.on(" ").omitEmptyStrings();
    private static final Splitter STACKTRACE_SPLITTER = Splitter.on(";").omitEmptyStrings();
    private static final Joiner STACKTRACE_JOINER = Joiner.on(";");

    private final SanitizeFunction sanitizeFunction;

    public FlameGraphSanitizer(SanitizeFunction sanitizeFunction) {
        this.sanitizeFunction = sanitizeFunction;
    }

    public void sanitize(final File in, File out) {
        IoActions.writeTextFile(out, new ErroringAction<BufferedWriter>() {
            @Override
            protected void doExecute(BufferedWriter writer) throws Exception {
                BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(in)));
                String line;
                StringBuilder sb = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    if (sanitizeFunction.skipLine(line)) {
                        continue;
                    }
                    List<String> data = LINE_SPLITTER.splitToList(line);
                    if (data.size() == 2) {
                        String stackTraces = data.get(0);
                        String suffix = data.get(1);
                        List<String> stackTraceElements = STACKTRACE_SPLITTER.splitToList(stackTraces);
                        List<String> remapped = new ArrayList<String>(stackTraceElements.size());
                        for (String stackTraceElement : stackTraceElements) {
                            String mapped = sanitizeFunction.map(stackTraceElement);
                            if (mapped != null) {
                                remapped.add(mapped);
                            }
                        }
                        if (!remapped.isEmpty()) {
                            sb.setLength(0);
                            STACKTRACE_JOINER.appendTo(sb, remapped);
                            sb.append(' ');
                            sb.append(suffix);
                            sb.append("\n");
                            writer.write(sb.toString());
                        }
                    }
                }
            }
        });
    }

    public interface SanitizeFunction {
        boolean skipLine(String line);

        String map(String entry);
    }

    public static class RegexBasedSanitizerFunction implements SanitizeFunction {
        private final Map<Pattern, String> replacements;

        public RegexBasedSanitizerFunction(Map<Pattern, String> replacements) {
            this.replacements = replacements;
        }

        @Override
        public boolean skipLine(String line) {
            return false;
        }

        @Override
        public String map(String entry) {
            for (Map.Entry<Pattern, String> replacement : replacements.entrySet()) {
                Matcher matcher = replacement.getKey().matcher(entry);
                String value = replacement.getValue();
                StringBuffer sb = new StringBuffer();
                while (matcher.find()) {
                    matcher.appendReplacement(sb, value);
                }
                matcher.appendTail(sb);
                if (sb.length() > 0) {
                    entry = sb.toString();
                }
            }
            return entry;
        }

    }
}
