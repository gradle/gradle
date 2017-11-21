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
import org.gradle.language.nativeplatform.internal.Expression;
import org.gradle.language.nativeplatform.internal.Include;
import org.gradle.language.nativeplatform.internal.IncludeDirectives;
import org.gradle.language.nativeplatform.internal.IncludeType;
import org.gradle.language.nativeplatform.internal.Macro;
import org.gradle.language.nativeplatform.internal.MacroFunction;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
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
                        consumeDefineBody(line, pos, macros, macroFunctions);
                    } else if (line.startsWith("include", pos)) {
                        pos += 7;
                        consumeIncludeOrImportBody(line, pos, false, includes);
                    } else if (line.startsWith("import", pos)) {
                        pos += 6;
                        consumeIncludeOrImportBody(line, pos, true, includes);
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

    private void consumeIncludeOrImportBody(CharSequence line, int startPos, boolean isImport, List<Include> includes) {
        if (startPos == line.length()) {
            // No include expression
            return;
        }
        if (consumeIdentifier(line, startPos) != startPos) {
            // Some other directive
            return;
        }
        Expression expression = parseExpression(line, startPos, line.length());
        if (expression.getType() != IncludeType.OTHER || !expression.getValue().isEmpty()) {
            includes.add(IncludeWithSimpleExpression.create(expression, isImport));
        }
        // Ignore includes with no value
    }

    private void consumeDefineBody(CharSequence line, int startPos, List<Macro> macros, List<MacroFunction> macroFunctions) {
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
        String name = line.subSequence(startName, endName).toString();
        if (endName < line.length() && line.charAt(endName) == '(') {
            // A function-like macro
            consumeMacroFunctionDirectiveBody(line, endName + 1, name, macroFunctions);
        } else {
            // An object-like macro
            consumeMacroDirectiveBody(line, endName, name, macros);
        }
    }

    private void consumeMacroDirectiveBody(CharSequence line, int startBody, String macroName, List<Macro> macros) {
        Expression expression = parseExpression(line, startBody, line.length());
        if (expression.getType() == IncludeType.MACRO_FUNCTION && !expression.getArguments().isEmpty()) {
            macros.add(new MacroWithMacroFunctionExpression(macroName, expression.getValue(), expression.getArguments()));
        } else if (expression.getType() != IncludeType.OTHER) {
            macros.add(new MacroWithSimpleExpression(macroName, expression.getType(), expression.getValue()));
        } else {
            // Discard the body when the expression is not resolvable
            macros.add(new UnresolveableMacro(macroName));
        }
    }

    private void consumeMacroFunctionDirectiveBody(CharSequence line, int startArgs, String macroName, List<MacroFunction> macroFunctions) {
        int pos = consumeWhitespace(line, startArgs);
        List<String> paramNames = new ArrayList<String>();
        int next = consumeIdentifier(line, pos);
        if (next != pos) {
            while (true) {
                paramNames.add(line.subSequence(pos, next).toString());
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
        Expression expression = parseExpression(line, endArgs, line.length());
        if (expression.getType() == IncludeType.QUOTED || expression.getType() == IncludeType.SYSTEM) {
            // Returns a fixed value expression
            macroFunctions.add(new ReturnFixedValueMacroFunction(macroName, paramNames.size(), expression.getType(), expression.getValue(), Collections.<Expression>emptyList()));
            return;
        }
        if (expression.getType() == IncludeType.MACRO) {
            for (int i = 0; i < paramNames.size(); i++) {
                String name = paramNames.get(i);
                if (name.equals(expression.getValue())) {
                    // Returns a parameter
                    macroFunctions.add(new ReturnParameterMacroFunction(macroName, paramNames.size(), i));
                    return;
                }
            }
            // References some fixed value expression, return it
            macroFunctions.add(new ReturnFixedValueMacroFunction(macroName, paramNames.size(), expression.getType(), expression.getValue(), Collections.<Expression>emptyList()));
            return;
        }
        if (expression.getType() == IncludeType.MACRO_FUNCTION && paramNames.isEmpty()) {
            // Handle zero args function that returns a macro function call
            macroFunctions.add(new ReturnFixedValueMacroFunction(macroName, paramNames.size(), expression.getType(), expression.getValue(), expression.getArguments()));
            return;
        }

        // Not resolvable. Discard the body when the expression is not resolvable
        macroFunctions.add(new UnresolveableMacroFunction(macroName, paramNames.size()));
    }

    static Expression parseExpression(String value) {
        return parseExpression(value, 0, value.length());
    }

    private static Expression parseExpression(CharSequence value, int startPos, int endPos) {
        int pos = consumeWhitespace(value, startPos);
        if (pos >= endPos) {
            // Empty or only whitespace
            return new DefaultExpression(value.subSequence(startPos, endPos).toString().trim(), IncludeType.OTHER);
        }
        char ch = value.charAt(pos);
        if (ch == '<') {
            return parseDelimitedExpression(value, startPos, endPos, pos, '>', IncludeType.SYSTEM);
        } else if (ch == '"') {
            return parseDelimitedExpression(value, startPos, endPos, pos, '"', IncludeType.QUOTED);
        }

        int startName = pos;
        int endName = consumeIdentifier(value, startName);
        if (endName == startName) {
            // No identifier
            return new DefaultExpression(value.subSequence(startPos, endPos).toString().trim(), IncludeType.OTHER);
        }
        pos = consumeWhitespace(value, endName);
        if (pos >= endPos) {
            // Just an identifier, this is a macro reference
            return new DefaultExpression(value.subSequence(startName, endName).toString(), IncludeType.MACRO);
        }
        if (value.charAt(pos) == '(') {
            // A macro function reference
            pos++;
            pos = consumeWhitespace(value, pos);
            if (pos < endPos) {
                List<Expression> argumentExpressions = new ArrayList<Expression>();
                pos = consumeArgumentList(value, pos, argumentExpressions);
                if (pos < endPos && value.charAt(pos) == ')') {
                    pos = consumeWhitespace(value, pos + 1);
                    if (pos >= endPos) {
                        String name = value.subSequence(startName, endName).toString();
                        if (argumentExpressions.isEmpty()) {
                            return new DefaultExpression(name, IncludeType.MACRO_FUNCTION);
                        } else {
                            return new MacroFunctionExpression(name, argumentExpressions);
                        }
                    }
                }
            }
        }

        return new DefaultExpression(value.subSequence(startPos, endPos).toString().trim(), IncludeType.OTHER);
    }

    private static int consumeArgumentList(CharSequence value, int startParams, List<Expression> expressions) {
        int pos = startParams;
        // Only handle macro expressions for now
        int end = consumeIdentifier(value, pos);
        if (end == pos) {
            // Not an identifier
            return end;
        }
        expressions.add(new DefaultExpression(value.subSequence(pos, end).toString(), IncludeType.MACRO));
        pos = end;
        while (true) {
            pos = consumeWhitespace(value, pos);
            if (pos == value.length() || value.charAt(pos) != ',') {
                return pos;
            }
            pos++;
            pos = consumeWhitespace(value, pos);
            end = consumeIdentifier(value, pos);
            if (end == pos) {
                return pos;
            }
            expressions.add(new DefaultExpression(value.subSequence(pos, end).toString(), IncludeType.MACRO));
            pos = end;
        }
    }

    private static Expression parseDelimitedExpression(CharSequence value, int startPos, int endPos, int startDelim, char endDelim, IncludeType type) {
        int pos = startDelim + 1;
        int startValue = pos;
        while (pos < endPos && value.charAt(pos) != endDelim) {
            pos++;
        }
        if (pos >= endPos) {
            // No terminating delimiter
            return new DefaultExpression(value.subSequence(startPos, endPos).toString().trim(), IncludeType.OTHER);
        }
        int endValue = pos;
        pos++;
        pos = consumeWhitespace(value, pos);
        if (pos != endPos) {
            // Extra stuff
            return new DefaultExpression(value.subSequence(startPos, endPos).toString().trim(), IncludeType.OTHER);
        }
        return new DefaultExpression(value.subSequence(startValue, endValue).toString(), type);
    }

    /**
     * Finds the end of an identifier.
     */
    private static int consumeIdentifier(CharSequence value, int startOffset) {
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
    private static int consumeWhitespace(CharSequence value, int startOffset) {
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

    private static class MacroFunctionExpression extends AbstractExpression {
        private final String value;
        private final List<Expression> arguments;

        MacroFunctionExpression(String value, List<Expression> arguments) {
            this.value = value;
            this.arguments = arguments;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public IncludeType getType() {
            return IncludeType.MACRO_FUNCTION;
        }

        @Override
        public List<Expression> getArguments() {
            return arguments;
        }
    }
}
