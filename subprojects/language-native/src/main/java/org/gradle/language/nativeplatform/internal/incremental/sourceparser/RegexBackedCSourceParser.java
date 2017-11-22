/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.language.nativeplatform.internal.incremental.sourceparser;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import org.apache.commons.io.IOUtils;
import org.gradle.api.UncheckedIOException;
import org.gradle.language.nativeplatform.internal.Include;
import org.gradle.language.nativeplatform.internal.IncludeDirectives;
import org.gradle.language.nativeplatform.internal.IncludeType;
import org.gradle.language.nativeplatform.internal.Macro;
import org.gradle.language.nativeplatform.internal.MacroFunction;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.List;

public class RegexBackedCSourceParser implements CSourceParser {
    @Override
    public IncludeDirectives parseSource(File sourceFile) {
        List<Include> includes = Lists.newArrayList();
        List<Macro> macros = Lists.newArrayList();
        List<MacroFunction> macroFunctions = Lists.newArrayList();
        try {
            BufferedReader bf = new BufferedReader(new PreprocessingReader(new BufferedReader(new FileReader(sourceFile))));
            try {
                String line;
                while ((line = bf.readLine()) != null) {
                    int pos = consumeWhitespace(line, 0);
                    if (pos < line.length() && line.charAt(pos) != '#') {
                        continue;
                    }
                    pos++;
                    pos = consumeWhitespace(line, pos);
                    if (pos == line.length()) {
                        continue;
                    }
                    if (line.startsWith("define", pos)) {
                        pos += 6;
                        consumeDefine(line, pos, macros, macroFunctions);
                    } else if (line.startsWith("include", pos)) {
                        pos += 7;
                        consumeIncludeOrImport(line, pos, false, includes);
                    } else if (line.startsWith("import", pos)) {
                        pos += 6;
                        consumeIncludeOrImport(line, pos, true, includes);
                    }
                }
            } finally {
                IOUtils.closeQuietly(bf);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new DefaultIncludeDirectives(ImmutableList.copyOf(includes), ImmutableList.copyOf(macros), ImmutableList.copyOf(macroFunctions));
    }

    private void consumeIncludeOrImport(String line, int startPos, boolean isImport, List<Include> includes) {
        if (startPos == line.length()) {
            // No include expression
            return;
        }
        if (consumeIdentifier(line, startPos) != startPos) {
            // Some other directive
            return;
        }
        int startValue = consumeWhitespace(line, startPos);
        String value = line.substring(startValue).trim();
        if (value.isEmpty()) {
            // No value
            return;
        }
        includes.add(DefaultInclude.parse(value, isImport));
    }

    private void consumeDefine(String line, int startPos, List<Macro> macros, List<MacroFunction> macroFunctions) {
        int startName = consumeWhitespace(line, startPos);
        if (startName == startPos) {
            // No separating whitespace
            return;
        }
        int endName = consumeIdentifier(line, startName);
        if (endName == startName) {
            // No macro name
            return;
        }
        String name = line.substring(startName, endName);
        if (endName < line.length() && line.charAt(endName) == '(') {
            // A function
            int pos = consumeWhitespace(line, endName + 1);
            int next = consumeIdentifier(line, pos);
            int parameters = 0;
            if (next != pos) {
                while (true) {
                    parameters++;
                    pos = consumeWhitespace(line, next);
                    if (pos == line.length()) {
                        // Unexpected end of line
                        return;
                    }
                    if (line.charAt(pos) == ')') {
                        break;
                    }
                    if (line.charAt(pos) != ',') {
                        // Missing ','
                        return;
                    }
                    pos++;
                    pos = consumeWhitespace(line, pos);
                    next = consumeIdentifier(line, pos);
                    if (next == pos) {
                        // Not an identifier
                        return;
                    }
                }
            }
            pos = consumeWhitespace(line, pos);
            if (pos == line.length() || line.charAt(pos) != ')') {
                // Badly form args list
                return;
            }
            int endArgs = pos + 1;
            String value = line.substring(endArgs).trim();
            Include include = DefaultInclude.parse(value, false);
            if (include.getType() == IncludeType.OTHER) {
                macroFunctions.add(new UnresolveableMacroFunction(name, parameters));
            } else {
                macroFunctions.add(new DefaultMacroFunction(name, parameters, include.getType(), include.getValue()));
            }
        } else {
            // An object-like macro
            String value = line.substring(endName).trim();
            Include include = DefaultInclude.parse(value, false);
            if (include.getType() == IncludeType.OTHER) {
                macros.add(new UnresolveableMacro(name));
            } else {
                macros.add(new DefaultMacro(name, include.getType(), include.getValue()));
            }
        }
    }

    /**
     * Finds the end of an identifier.
     */
    static int consumeIdentifier(CharSequence value, int startOffset) {
        int pos = startOffset;
        while (pos < value.length()) {
            char ch = value.charAt(pos);
            if (!Character.isLetterOrDigit(ch) && ch != '_' && ch != '$') {
                break;
            }
            pos++;
        }
        return pos;
    }

    /**
     * Finds the end of a sequence of whitespace characters.
     */
    static int consumeWhitespace(CharSequence value, int startOffset) {
        int pos = startOffset;
        while (pos < value.length()) {
            char ch = value.charAt(pos);
            if (!Character.isWhitespace(ch) && ch != 0) {
                break;
            }
            pos++;
        }
        return pos;
    }
}
