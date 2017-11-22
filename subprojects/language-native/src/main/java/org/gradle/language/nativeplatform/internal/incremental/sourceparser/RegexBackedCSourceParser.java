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

import javax.annotation.Nullable;
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
            BufferedReader reader = new BufferedReader(new FileReader(sourceFile));
            try {
                PreprocessingReader lineReader = new PreprocessingReader(reader);
                Buffer buffer = new Buffer();
                while (true) {
                    buffer.reset();
                    if (!lineReader.readNextLine(buffer.value)) {
                        break;
                    }
                    buffer.consumeWhitespace();
                    if (!buffer.consume('#')) {
                        continue;
                    }
                    buffer.consumeWhitespace();
                    if (buffer.consume("define")) {
                        parseDefineDirectiveBody(buffer, macros, macroFunctions);
                    } else if (buffer.consume("include")) {
                        parseIncludeOrImportDirectiveBody(buffer, false, includes);
                    } else if (buffer.consume("import")) {
                        parseIncludeOrImportDirectiveBody(buffer, true, includes);
                    }
                }
            } finally {
                IOUtils.closeQuietly(reader);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return new DefaultIncludeDirectives(ImmutableList.copyOf(includes), ImmutableList.copyOf(macros), ImmutableList.copyOf(macroFunctions));
    }

    /**
     * Parses an #include/#import directive body. Consumes all input.
     */
    private void parseIncludeOrImportDirectiveBody(Buffer buffer, boolean isImport, List<Include> includes) {
        if (!buffer.hasAny()) {
            // No include expression, ignore
            return;
        }
        if (buffer.hasIdentifierChar()) {
            // An identifier with no separator, so this is not an #include or #import directive, it is some other directive
            return;
        }
        Expression expression = parseExpression(buffer);
        if (expression.getType() != IncludeType.OTHER || !expression.getValue().isEmpty()) {
            includes.add(IncludeWithSimpleExpression.create(expression, isImport));
        }
        // Ignore includes with no value
    }

    /**
     * Parses a #define directive body. Consumes all input.
     */
    private void parseDefineDirectiveBody(Buffer buffer, List<Macro> macros, List<MacroFunction> macroFunctions) {
        if (!buffer.consumeWhitespace()) {
            // No separating whitespace between the #define and the name
            return;
        }
        String name = buffer.readIdentifier();
        if (name == null) {
            // No macro name
            return;
        }
        if (buffer.consume('(')) {
            // A function-like macro
            parseMacroFunctionDirectiveBody(buffer, name, macroFunctions);
        } else {
            // An object-like macro
            parseMacroObjectDirectiveBody(buffer, name, macros);
        }
    }

    /**
     * Parse an "object-like" macro directive body. Consumes all input.
     */
    private void parseMacroObjectDirectiveBody(Buffer buffer, String macroName, List<Macro> macros) {
        Expression expression = parseExpression(buffer);
        if (expression.getType() == IncludeType.MACRO_FUNCTION && !expression.getArguments().isEmpty()) {
            macros.add(new MacroWithMacroFunctionCallExpression(macroName, expression.getValue(), expression.getArguments()));
        } else if (expression.getType() != IncludeType.OTHER) {
            macros.add(new MacroWithSimpleExpression(macroName, expression.getType(), expression.getValue()));
        } else {
            // Discard the body when the expression is not resolvable
            macros.add(new UnresolveableMacro(macroName));
        }
    }

    /**
     * Parse a "function-like" macro directive body. Consumes all input.
     */
    private void parseMacroFunctionDirectiveBody(Buffer buffer, String macroName, List<MacroFunction> macroFunctions) {
        buffer.consumeWhitespace();
        List<String> paramNames = new ArrayList<String>();
        consumeParameterList(buffer, paramNames);
        if (!buffer.consume(')')) {
            // Badly form args list
            return;
        }
        Expression expression = parseExpression(buffer);
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

    private void consumeParameterList(Buffer buffer, List<String> paramNames) {
        String paramName = buffer.readIdentifier();
        while (paramName != null) {
            paramNames.add(paramName);
            buffer.consumeWhitespace();
            if (!buffer.consume(',')) {
                // Missing ','
                return;
            }
            buffer.consumeWhitespace();
            paramName = buffer.readIdentifier();
            if (paramName == null) {
                // Missing parameter name
                return;
            }
        }
    }

    static Expression parseExpression(String value) {
        Buffer buffer = new Buffer();
        buffer.value.append(value);
        return parseExpression(buffer);
    }

    /**
     * Parses an expression that forms the body of a directive. Consumes all of the input.
     */
    private static Expression parseExpression(Buffer buffer) {
        int startPos = buffer.pos;
        Expression expression = readExpression(buffer);
        buffer.consumeWhitespace();
        if (expression == null || buffer.hasAny()) {
            return new SimpleExpression(buffer.substring(startPos).trim(), IncludeType.OTHER);
        }
        return expression;
    }

    /**
     * Parses an expression that forms the body of a directive. Returns null when an expression cannot be parsed and consumes input up to the parse failure point.
     */
    @Nullable
    private static Expression readExpression(Buffer buffer) {
        buffer.consumeWhitespace();
        if (!buffer.hasAny()) {
            // Empty or only whitespace
            return null;
        }

        if (buffer.consume('<')) {
            return readDelimitedExpression(buffer, '>', IncludeType.SYSTEM);
        } else if (buffer.consume('"')) {
            return readDelimitedExpression(buffer, '"', IncludeType.QUOTED);
        }

        String identifier = buffer.readIdentifier();
        if (identifier == null) {
            // No identifier
            return null;
        }
        buffer.consumeWhitespace();
        if (!buffer.consume('(')) {
            // Just an identifier, this is a macro reference
            return new SimpleExpression(identifier, IncludeType.MACRO);
        }

        // A macro function call
        buffer.consumeWhitespace();
        List<Expression> argumentExpressions = new ArrayList<Expression>();
        consumeArgumentList(buffer, argumentExpressions);
        if (!buffer.consume(')')) {
            // Badly formed arguments
            return null;
        }
        if (argumentExpressions.isEmpty()) {
            return new SimpleExpression(identifier, IncludeType.MACRO_FUNCTION);
        } else {
            return new MacroFunctionCallExpression(identifier, argumentExpressions);
        }
    }

    /**
     * Parses a macro function argument list. Stops when unable to recognize any further arguments.
     */
    private static void consumeArgumentList(Buffer buffer, List<Expression> expressions) {
        Expression expression = readExpression(buffer);
        if (expression == null) {
            // Not an expression
            return;
        }
        expressions.add(expression);
        while (true) {
            buffer.consumeWhitespace();
            if (!buffer.consume(',')) {
                return;
            }
            expression = readExpression(buffer);
            if (expression == null) {
                return;
            }
            expressions.add(expression);
        }
    }

    /**
     * Parses an expression that ends with the given delimiter. Returns null on failure and consumes input up to the parse failure point.
     */
    @Nullable
    private static Expression readDelimitedExpression(Buffer buffer, char endDelim, IncludeType type) {
        int startValue = buffer.pos;
        buffer.consumeUpTo(endDelim);
        int endValue = buffer.pos;
        if (!buffer.consume(endDelim)) {
            return null;
        }
        return new SimpleExpression(buffer.value.substring(startValue, endValue), type);
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

    private static class Buffer {
        final StringBuilder value = new StringBuilder();
        int pos = 0;

        @Override
        public String toString() {
            return "{buffer remaining: '" + value.substring(pos, pos + Math.min(value.length() - pos, 20)) + "'}";
        }

        void reset() {
            value.setLength(0);
            pos = 0;
        }

        /**
         * Returns text from the specified location to the end of the buffer.
         */
        String substring(int pos) {
            return value.substring(pos);
        }

        /**
         * Is there another character available? Does not consume the character.
         */
        boolean hasAny() {
            return pos < value.length();
        }

        /**
         * Is the given character available? Does not consume the character.
         */
        public boolean has(char c) {
            return pos < value.length() && value.charAt(pos) == c;
        }

        /**
         * Is there an identifier character at the current location? Does not consume the character.
         */
        boolean hasIdentifierChar() {
            if (pos < value.length()) {
                char ch = value.charAt(pos);
                return Character.isLetterOrDigit(ch) || ch == '_' || ch == '$';
            }
            return false;
        }

        /**
         * Reads an identifier from the current location. Does not consume anything if there is not an identifier at the current location.
         *
         * @return the identifier or null if none present.
         */
        @Nullable
        String readIdentifier() {
            int oldPos = pos;
            pos = RegexBackedCSourceParser.consumeIdentifier(value, pos);
            if (pos == oldPos) {
                return null;
            }
            return value.substring(oldPos, pos);
        }

        /**
         * Skip any whitespace at the current location.
         *
         * @return true if skipped, false if not.
         */
        boolean consumeWhitespace() {
            int oldPos = pos;
            pos = RegexBackedCSourceParser.consumeWhitespace(value, pos);
            return pos != oldPos;
        }

        /**
         * Skip the given string if present at the current location.
         *
         * @return true if skipped, false if not.
         */
        boolean consume(String token) {
            if (pos + token.length() < value.length()) {
                for (int i = 0; i < token.length(); i++) {
                    if (value.charAt(pos + i) != token.charAt(i)) {
                        return false;
                    }
                }
                pos += token.length();
                return true;
            }
            return false;
        }

        /**
         * Skip the given character if present at the current location.
         *
         * @return true if skipped, false if not.
         */
        boolean consume(char c) {
            if (pos < value.length() && value.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        /**
         * Skip characters up to the given character. Does not consume the character.
         */
        void consumeUpTo(char c) {
            while (pos < value.length() && value.charAt(pos) != c) {
                pos++;
            }
        }
    }

}
