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
import com.google.common.collect.Sets;
import org.gradle.api.GradleException;
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
import java.io.Reader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;

/**
 * Parses a subset of the C preprocessor language, to extract details of {@code #include}, {@code #import} and {@code #define} directives. Only handles a subset of the possible expressions that can be
 * used as the body of these directives.
 */
public class RegexBackedCSourceParser implements CSourceParser {
    @Override
    public IncludeDirectives parseSource(File sourceFile) {
        try (Reader fileReader = new FileReader(sourceFile)) {
            return parseSource(fileReader);
        } catch (Exception e) {
            throw new GradleException(String.format("Could not extract includes from source file %s.", sourceFile), e);
        }
    }

    protected IncludeDirectives parseSource(Reader sourceReader) throws IOException {
        Set<Include> includes = Sets.newLinkedHashSet();
        List<Macro> macros = Lists.newArrayList();
        List<MacroFunction> macroFunctions = Lists.newArrayList();
        BufferedReader reader = new BufferedReader(sourceReader);
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
        return DefaultIncludeDirectives.of(ImmutableList.copyOf(includes), ImmutableList.copyOf(macros), ImmutableList.copyOf(macroFunctions));
    }

    /**
     * Parses an #include/#import directive body. Consumes all input.
     */
    private void parseIncludeOrImportDirectiveBody(Buffer buffer, boolean isImport, Collection<Include> includes) {
        if (!buffer.hasAny()) {
            // No include expression, ignore
            return;
        }
        if (buffer.hasIdentifierChar()) {
            // An identifier with no separator, so this is not an #include or #import directive, it is some other directive
            return;
        }
        Expression expression = parseDirectiveBodyExpression(buffer);
        if (expression.getType() == IncludeType.TOKEN_CONCATENATION || expression.getType() == IncludeType.ARGS_LIST || expression.getType() == IncludeType.EXPRESSIONS) {
            // Token concatenation is only allowed inside a #define body
            // Arbitrary tokens won't resolve to an include path
            // Treat both these cases as an unresolvable include directive
            expression = new SimpleExpression(expression.getAsSourceText(), IncludeType.OTHER);
        }
        expression = expression.asMacroExpansion();
        if (expression.getType() != IncludeType.OTHER || !expression.getValue().isEmpty()) {
            // Either a resolvable expression or a non-empty unresolvable expression, collect. Ignore includes with no value
            includes.add(IncludeWithSimpleExpression.create(expression, isImport));
        }
    }

    /**
     * Parses a #define directive body. Consumes all input.
     */
    private void parseDefineDirectiveBody(Buffer buffer, Collection<Macro> macros, Collection<MacroFunction> macroFunctions) {
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
    private void parseMacroObjectDirectiveBody(Buffer buffer, String macroName, Collection<Macro> macros) {
        Expression expression = parseDirectiveBodyExpression(buffer);
        expression = expression.asMacroExpansion();
        if (!expression.getArguments().isEmpty()) {
            // Body is an expression with one or more arguments
            macros.add(new MacroWithComplexExpression(macroName, expression.getType(), expression.getValue(), expression.getArguments()));
        } else if (expression.getType() != IncludeType.OTHER) {
            // Body is a simple expression, including a macro function call with no arguments
            macros.add(new MacroWithSimpleExpression(macroName, expression.getType(), expression.getValue()));
        } else {
            // Discard the body when the expression is not resolvable
            macros.add(new UnresolvableMacro(macroName));
        }
    }

    /**
     * Parse a "function-like" macro directive body. Consumes all input.
     */
    private void parseMacroFunctionDirectiveBody(Buffer buffer, String macroName, Collection<MacroFunction> macroFunctions) {
        buffer.consumeWhitespace();
        List<String> paramNames = new ArrayList<String>();
        consumeParameterList(buffer, paramNames);
        if (!buffer.consume(')')) {
            // Badly form args list
            return;
        }
        Expression expression = parseDirectiveBodyExpression(buffer);
        if (expression.getType() == IncludeType.QUOTED || expression.getType() == IncludeType.SYSTEM) {
            // Returns a fixed value expression
            macroFunctions.add(new ReturnFixedValueMacroFunction(macroName, paramNames.size(), expression.getType(), expression.getValue(), Collections.<Expression>emptyList()));
            return;
        }
        if (expression.getType() == IncludeType.IDENTIFIER) {
            for (int i = 0; i < paramNames.size(); i++) {
                String name = paramNames.get(i);
                if (name.equals(expression.getValue())) {
                    // Returns a parameter
                    macroFunctions.add(new ReturnParameterMacroFunction(macroName, paramNames.size(), i));
                    return;
                }
            }
            // References some fixed value expression, return it after macro expanding
            macroFunctions.add(new ReturnFixedValueMacroFunction(macroName, paramNames.size(), IncludeType.MACRO, expression.getValue(), Collections.<Expression>emptyList()));
            return;
        }

        if (expression.getType() != IncludeType.OTHER) {
            // Look for parameter substitutions
            if (paramNames.isEmpty() || expression.getArguments().isEmpty()) {
                // When this function has no parameters, we don't need to substitute parameters, so return the expression after macro expanding it
                // Also handle calling a zero args function, as we also don't need to substitute parameters
                expression = expression.asMacroExpansion();
                macroFunctions.add(new ReturnFixedValueMacroFunction(macroName, paramNames.size(), expression.getType(), expression.getValue(), expression.getArguments()));
                return;
            }
            List<Integer> argsMap = new ArrayList<Integer>(expression.getArguments().size());
            boolean usesArgs = mapArgs(paramNames, expression, argsMap);
            if (!usesArgs) {
                // Don't need to do parameter substitution, return the value of the expression after macro expanding it
                expression = expression.asMacroExpansion();
                macroFunctions.add(new ReturnFixedValueMacroFunction(macroName, paramNames.size(), expression.getType(), expression.getValue(), expression.getArguments()));
            } else {
                //Need to do parameter substitution, return the value of the expression after parameter substitutions and macro expanding the result
                int[] argsMapArray = new int[argsMap.size()];
                for (int i = 0; i < argsMap.size(); i++) {
                    argsMapArray[i] = argsMap.get(i);
                }
                expression = expression.asMacroExpansion();
                macroFunctions.add(new ArgsMappingMacroFunction(macroName, paramNames.size(), argsMapArray, expression.getType(), expression.getValue(), expression.getArguments()));
            }
            return;
        }

        // Not resolvable. Discard the body when the expression is not resolvable
        macroFunctions.add(new UnresolvableMacroFunction(macroName, paramNames.size()));
    }

    private boolean mapArgs(List<String> paramNames, Expression expression, List<Integer> argsMap) {
        boolean usesParameters = false;
        for (int i = 0; i < expression.getArguments().size(); i++) {
            Expression argument = expression.getArguments().get(i);
            if (argument.getType() == IncludeType.IDENTIFIER) {
                boolean matches = false;
                for (int j = 0; j < paramNames.size(); j++) {
                    String paramName = paramNames.get(j);
                    if (argument.getValue().equals(paramName)) {
                        argsMap.add(j);
                        usesParameters = true;
                        matches = true;
                        break;
                    }
                }
                if (matches) {
                    continue;
                }
            }
            if (argument.getArguments().isEmpty()) {
                // Don't map
                argsMap.add(ArgsMappingMacroFunction.KEEP);
                continue;
            }
            List<Integer> nestedMap = new ArrayList<Integer>(argument.getArguments().size());
            boolean argUsesParameters = mapArgs(paramNames, argument, nestedMap);
            if (argUsesParameters) {
                argsMap.add(ArgsMappingMacroFunction.REPLACE_ARGS);
                argsMap.addAll(nestedMap);
            } else {
                argsMap.add(ArgsMappingMacroFunction.KEEP);
            }
            usesParameters |= argUsesParameters;
        }
        return usesParameters;
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

    public static Expression parseExpression(String value) {
        Buffer buffer = new Buffer();
        buffer.value.append(value);
        return parseDirectiveBodyExpression(buffer).asMacroExpansion();
    }

    /**
     * Parses an expression that forms the body of a directive. Consumes all of the input.
     */
    private static Expression parseDirectiveBodyExpression(Buffer buffer) {
        int startPos = buffer.pos;
        Expression expression = parseExpression(buffer);
        buffer.consumeWhitespace();
        if (expression == null || buffer.hasAny()) {
            // Unrecognized expression or extra stuff after the expression, possibly another expression
            return new SimpleExpression(buffer.substring(startPos).trim(), IncludeType.OTHER);
        }
        return expression;
    }

    /**
     * Parses an expression that forms the body of a directive or a macro function parameter:
     *
     * - A path or string
     * - An argument list, ie zero or more expressions delimited by '(' and ')'
     * - An identifier
     * - Token concatenation
     * - A macro function call
     * - Any single character that does not delimit function arguments
     *
     * Returns null when an expression cannot be parsed and consumes input up to the parse failure point.
     */
    @Nullable
    private static Expression parseExpression(Buffer buffer) {
        buffer.consumeWhitespace();
        if (!buffer.hasAny()) {
            // Empty or only whitespace
            return null;
        }

        Expression expression = readPathExpression(buffer);
        if (expression != null) {
            // A path, either "" or <> delimited
            return expression;
        }

        // A sequence of tokens that look like a function call argument list. Should support an arbitrary token sequence
        List<Expression> arguments = readArgumentList(buffer);
        if (arguments != null) {
            if (arguments.isEmpty()) {
                return SimpleExpression.EMPTY_ARGS;
            } else {
                return new ComplexExpression(IncludeType.ARGS_LIST, null, arguments);
            }
        }

        String identifier = buffer.readIdentifier();
        if (identifier == null) {
            // No identifier, allow anything except '(' or ',' or ')'
            String token = buffer.readAnyExcept("(),");
            if (token != null) {
                return new SimpleExpression(token, IncludeType.TOKEN);
            }
            return null;
        }

        // Either a macro function, a macro or token concatenation
        buffer.consumeWhitespace();

        arguments = readArgumentList(buffer);
        if (arguments != null) {
            // A macro function call
            if (arguments.isEmpty()) {
                return new SimpleExpression(identifier, IncludeType.MACRO_FUNCTION);
            } else {
                return new ComplexExpression(IncludeType.MACRO_FUNCTION, identifier, arguments);
            }
        }

        expression = readTokenConcatenation(buffer, identifier);
        if (expression != null) {
            return expression;
        }

        // Just an identifier, this is a token
        return new SimpleExpression(identifier, IncludeType.IDENTIFIER);
    }

    private static Expression readPathExpression(Buffer buffer) {
        if (buffer.consume('<')) {
            return readDelimitedExpression(buffer, '>', IncludeType.SYSTEM);
        } else if (buffer.consume('"')) {
            return readDelimitedExpression(buffer, '"', IncludeType.QUOTED);
        }
        return null;
    }

    /**
     * Reads a token concatenation expression. Does not consume anything if not present.
     */
    private static Expression readTokenConcatenation(Buffer buffer, String leftToken) {
        int pos = buffer.pos;
        if (!buffer.consume("##")) {
            return null;
        }
        buffer.consumeWhitespace();
        String right = buffer.readIdentifier();
        if (right == null) {
            // Need another identifier
            buffer.pos = pos;
            return null;
        }
        ComplexExpression concatExpression = new ComplexExpression(IncludeType.TOKEN_CONCATENATION, null, Arrays.<Expression>asList(new SimpleExpression(leftToken, IncludeType.IDENTIFIER), new SimpleExpression(right, IncludeType.IDENTIFIER)));

        buffer.consumeWhitespace();
        while (buffer.consume("##")) {
            buffer.consumeWhitespace();
            right = buffer.readIdentifier();
            if (right == null) {
                // Need another identifier
                buffer.pos = pos;
                return null;
            }
            concatExpression = new ComplexExpression(IncludeType.TOKEN_CONCATENATION, null, Arrays.<Expression>asList(concatExpression, new SimpleExpression(right, IncludeType.IDENTIFIER)));
            buffer.consumeWhitespace();
        }
        return concatExpression;
    }

    /**
     * Reads an argument list. Does not consume anything if an argument list is not present
     */
    private static List<Expression> readArgumentList(Buffer buffer) {
        int pos = buffer.pos;
        if (!buffer.consume('(')) {
            return null;
        }
        List<Expression> argumentExpressions = new ArrayList<Expression>();
        buffer.consumeWhitespace();
        consumeArgumentList(buffer, argumentExpressions);
        if (!buffer.consume(')')) {
            // Badly formed arguments
            buffer.pos = pos;
            return null;
        }
        return argumentExpressions;
    }

    /**
     * Parses a macro function argument list. Stops when unable to recognize any further arguments.
     */
    private static void consumeArgumentList(Buffer buffer, List<Expression> expressions) {
        Expression expression = readArgument(buffer);
        if (expression == null) {
            if (!buffer.has(',')) {
                // No args
                return;
            }
            expression = SimpleExpression.EMPTY_EXPRESSIONS;
        }
        expressions.add(expression);
        while (true) {
            buffer.consumeWhitespace();
            if (!buffer.consume(',')) {
                return;
            }
            expression = readArgument(buffer);
            if (expression == null) {
                expression = SimpleExpression.EMPTY_EXPRESSIONS;
            }
            expressions.add(expression);
        }
    }

    private static Expression readArgument(Buffer buffer) {
        Expression expression = parseExpression(buffer);
        if (expression == null) {
            return null;
        }
        buffer.consumeWhitespace();
        if (buffer.hasAny(",)")) {
            return expression;
        }
        List<Expression> expressions = new ArrayList<Expression>();
        expressions.add(expression);
        do {
            expression = parseExpression(buffer);
            if (expression == null) {
                return null;
            }
            expressions.add(expression);
            buffer.consumeWhitespace();
        } while (!buffer.hasAny(",)"));
        return new ComplexExpression(IncludeType.EXPRESSIONS, null, expressions);
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
         * Is one of the given character available? Does not consume the character.
         */
        public boolean hasAny(String chars) {
            if (pos >= value.length()) {
                return false;
            }
            char ch = value.charAt(pos);
            for (int i = 0; i < chars.length(); i++) {
                if (chars.charAt(i) == ch) {
                    return true;
                }
            }
            return false;
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
         * Reads any character except the given. Does not consume anything if there is no more input or one of the given chars are at the current location.
         *
         * @return the character or null if none present.
         */
        @Nullable
        String readAnyExcept(String chars) {
            if (pos >= value.length()) {
                return null;
            }
            char ch = value.charAt(pos);
            for (int i = 0; i < chars.length(); i++) {
                if (chars.charAt(i) == ch) {
                    return null;
                }
            }
            pos++;
            return String.valueOf(ch);
        }

        /**
         * Reads any character except the given. Does not consume anything if there is no more input or the given chars is at the current location.
         *
         * @return the character or null if none present.
         */
        @Nullable
        String readAnyExcept(char ch) {
            if (pos < value.length() && value.charAt(pos) != ch) {
                return value.substring(pos, ++pos);
            }
            return null;
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
