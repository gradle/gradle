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
package org.gradle.language.nativeplatform.internal.incremental;

import com.google.common.collect.LinkedHashMultimap;
import com.google.common.collect.Multimap;
import org.gradle.internal.FileUtils;
import org.gradle.language.nativeplatform.internal.Expression;
import org.gradle.language.nativeplatform.internal.Include;
import org.gradle.language.nativeplatform.internal.IncludeDirectives;
import org.gradle.language.nativeplatform.internal.IncludeType;
import org.gradle.language.nativeplatform.internal.Macro;
import org.gradle.language.nativeplatform.internal.MacroFunction;
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.ComplexExpression;
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.SimpleExpression;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class DefaultSourceIncludesResolver implements SourceIncludesResolver {
    private final List<File> includePaths;
    private final Map<File, Map<String, Boolean>> includeRoots;

    public DefaultSourceIncludesResolver(List<File> includePaths) {
        this.includePaths = includePaths;
        this.includeRoots = new HashMap<File, Map<String, Boolean>>();
    }

    @Override
    public IncludeResolutionResult resolveInclude(final File sourceFile, Include include, MacroLookup visibleMacros) {
        BuildableResult results = new BuildableResult();
        resolveExpression(visibleMacros, include, new PathResolvingVisitor(sourceFile, results), new TokenLookup());
        return results;
    }

    private void resolveExpression(MacroLookup visibleMacros, Expression expression, ExpressionVisitor visitor, TokenLookup tokenLookup) {
        if (expression.getType() == IncludeType.SYSTEM) {
            visitor.visitSystem(expression.getValue());
        } else if (expression.getType() == IncludeType.QUOTED) {
            visitor.visitQuoted(expression.getValue());
        } else if (expression.getType() == IncludeType.IDENTIFIER) {
            visitor.visitIdentifier(expression.getValue());
        } else if (expression.getType() == IncludeType.TOKENS) {
            visitor.visitTokens(expression.getArguments());
        } else {
            if (!visitor.startVisit(expression)) {
                // Skip, visitor is not interested
                return;
            }
            if (expression.getType() == IncludeType.MACRO) {
                resolveMacro(visibleMacros, expression, visitor, tokenLookup);
            } else if (expression.getType() == IncludeType.MACRO_FUNCTION) {
                resolveMacroFunction(visibleMacros, expression, visitor, tokenLookup);
            } else if (expression.getType() == IncludeType.TOKEN_CONCATENATION) {
                resolveTokenConcatenation(visibleMacros, expression, visitor, tokenLookup);
            } else {
                visitor.visitUnresolved();
            }
        }
    }

    private void resolveTokenConcatenation(final MacroLookup visibleMacros, final Expression expression, final ExpressionVisitor visitor, final TokenLookup tokenLookup) {
        final Expression left = expression.getArguments().get(0);
        final Expression right = expression.getArguments().get(1);

        if (left.getType() == IncludeType.IDENTIFIER && right.getType() == IncludeType.IDENTIFIER) {
            // Short circuit resolution
            resolveExpression(visibleMacros, new SimpleExpression(left.getValue() + right.getValue(), IncludeType.MACRO), visitor, tokenLookup);
            return;
        }

        if (tokenLookup.broken.contains(left) || tokenLookup.broken.contains(right)) {
            visitor.visitUnresolved();
            return;
        }

        if (!tokenLookup.stringTokensFor.containsKey(left)) {
            resolveExpression(visibleMacros, left, new CollectIdentifiers(tokenLookup, left), tokenLookup);
            if (tokenLookup.broken.contains(left)) {
                visitor.visitUnresolved();
                return;
            }
        }
        if (!tokenLookup.tokensFor.containsKey(right)) {
            resolveExpression(visibleMacros, right, new CollectTokens(tokenLookup, right), tokenLookup);
            if (tokenLookup.broken.contains(right)) {
                visitor.visitUnresolved();
                return;
            }
        }

        Collection<List<Expression>> rightValues = tokenLookup.tokensFor.get(right);
        Collection<String> leftValues = tokenLookup.stringTokensFor.get(left);
        for (String leftValue : leftValues) {
            for (List<Expression> rightValue : rightValues) {
                // Handle just empty string, single identifier or '(' params? ')', should handle more by parsing the tokens into an expression
                if (rightValue.size() == 0) {
                    resolveExpression(visibleMacros, new SimpleExpression(leftValue, IncludeType.MACRO), visitor, tokenLookup);
                    continue;
                }
                if (rightValue.size() == 1 && rightValue.get(0).getType() == IncludeType.IDENTIFIER) {
                    resolveExpression(visibleMacros, new SimpleExpression(leftValue + rightValue.get(0).getValue(), IncludeType.MACRO), visitor, tokenLookup);
                    continue;
                }
                if (rightValue.size() >= 2 && "(".equals(rightValue.get(0).getValue()) && ")".equals(rightValue.get(rightValue.size() - 1).getValue())) {
                    List<Expression> functionArgs = new ArrayList<Expression>();
                    int pos = 1;
                    if (rightValue.size() > 2) {
                        functionArgs.add(rightValue.get(1));
                        pos = 2;
                        while (pos < rightValue.size() - 2) {
                            if (!",".equals(rightValue.get(pos).getValue())) {
                                break;
                            }
                            pos++;
                            functionArgs.add(rightValue.get(pos));
                            pos++;
                        }
                    }
                    if (pos == rightValue.size() - 1) {
                        resolveExpression(visibleMacros, new ComplexExpression(IncludeType.MACRO_FUNCTION, leftValue, functionArgs), visitor, tokenLookup);
                        continue;
                    }
                }
                visitor.visitUnresolved();
            }
        }
    }

    private void resolveMacro(MacroLookup visibleMacros, Expression expression, ExpressionVisitor visitor, TokenLookup tokenLookup) {
        boolean found = false;
        for (IncludeDirectives includeDirectives : visibleMacros) {
            for (Macro macro : includeDirectives.getMacros()) {
                if (expression.getValue().equals(macro.getName())) {
                    found = true;
                    resolveExpression(visibleMacros, macro, visitor, tokenLookup);
                }
            }
        }
        if (!found) {
            visitor.visitIdentifier(expression.getValue());
        }
    }

    private void resolveMacroFunction(MacroLookup visibleMacros, Expression expression, ExpressionVisitor visitor, TokenLookup tokenLookup) {
        boolean found = false;
        for (IncludeDirectives includeDirectives : visibleMacros) {
            for (MacroFunction macro : includeDirectives.getMacrosFunctions()) {
                // Currently only handle functions with no parameters
                if (expression.getValue().equals(macro.getName())) {
                    List<Expression> arguments = expression.getArguments();
                    if (arguments.isEmpty() && macro.getParameterCount() == 1) {
                        // Provide an implicit empty argument
                        arguments = Collections.<Expression>singletonList(new SimpleExpression(null, IncludeType.TOKENS));
                    }
                    if (macro.getParameterCount() == arguments.size()) {
                        found = true;
                        Expression result = macro.evaluate(arguments);
                        resolveExpression(visibleMacros, result, visitor, tokenLookup);
                    }
                }
            }
        }
        if (!found) {
            visitor.visitUnresolved();
        }
    }

    private List<File> prependSourceDir(File sourceFile, List<File> includePaths) {
        List<File> quotedSearchPath = new ArrayList<File>(includePaths.size() + 1);
        quotedSearchPath.add(sourceFile.getParentFile());
        quotedSearchPath.addAll(includePaths);
        return quotedSearchPath;
    }

    private void searchForDependency(List<File> searchPath, String include, BuildableResult dependencies) {
        for (File searchDir : searchPath) {
            File candidate = new File(searchDir, include);

            Map<String, Boolean> searchedIncludes = includeRoots.get(searchDir);
            if (searchedIncludes == null) {
                searchedIncludes = new HashMap<String, Boolean>();
                includeRoots.put(searchDir, searchedIncludes);
            }
            dependencies.searched(candidate);
            if (searchedIncludes.containsKey(include)) {
                if (searchedIncludes.get(include)) {
                    dependencies.resolved(FileUtils.canonicalize(candidate));
                    return;
                }
                continue;
            }

            boolean found = candidate.isFile();
            searchedIncludes.put(include, found);

            if (found) {
                dependencies.resolved(FileUtils.canonicalize(candidate));
                return;
            }
        }
    }

    private static class TokenLookup {
        final Set<Expression> broken = new HashSet<Expression>();
        final Multimap<Expression, String> stringTokensFor = LinkedHashMultimap.create();
        final Multimap<Expression, List<Expression>> tokensFor = LinkedHashMultimap.create();
    }

    private interface ExpressionVisitor {
        /**
         * Called when an expression is about to be visited. Called for each intermediate expression as macros are expanded.
         *
         * @return true if the visit should continue, false to skip this expression.
         */
        boolean startVisit(Expression expression);

        /**
         * Called when an expression resolves to a quoted path.
         */
        void visitQuoted(String value);

        /**
         * Called when an expression resolves to a system path.
         */
        void visitSystem(String value);

        /**
         * Called when an expression resolves to a single identifier that could not be macro expanded.
         */
        void visitIdentifier(String value);

        /**
         * Called when an expression resolves to zero or more tokens.
         */
        void visitTokens(List<Expression> tokens);

        /**
         * Called when an expression could not be resolved to a value.
         */
        void visitUnresolved();
    }

    private static class BuildableResult implements IncludeResolutionResult {
        private final Set<File> files = new LinkedHashSet<File>();
        private final Set<File> candidates = new LinkedHashSet<File>();
        private boolean missing;

        void searched(File candidate) {
            candidates.add(candidate);
        }

        void resolved(File file) {
            files.add(file);
        }

        void unresolved() {
            missing = true;
        }

        @Override
        public boolean isComplete() {
            return !missing;
        }

        @Override
        public Collection<File> getFiles() {
            return files;
        }

        @Override
        public Collection<File> getCheckedLocations() {
            return candidates;
        }
    }

    private static class CollectIdentifiers implements ExpressionVisitor {
        private final Set<Expression> seen = new HashSet<Expression>();
        private final TokenLookup tokenLookup;
        private final Expression expression;

        CollectIdentifiers(TokenLookup tokenLookup, Expression expression) {
            this.tokenLookup = tokenLookup;
            this.expression = expression;
        }

        @Override
        public boolean startVisit(Expression expression) {
            return seen.add(expression);
        }

        @Override
        public void visitQuoted(String value) {
            visitUnresolved();
        }

        @Override
        public void visitSystem(String value) {
            visitUnresolved();
        }

        @Override
        public void visitIdentifier(String value) {
            tokenLookup.stringTokensFor.put(expression, value);
        }

        @Override
        public void visitTokens(List<Expression> tokens) {
            // Should handle this, but currently don't
            visitUnresolved();
        }

        @Override
        public void visitUnresolved() {
            tokenLookup.broken.add(expression);
        }
    }

    private static class CollectTokens implements ExpressionVisitor {
        private final Set<Expression> seen = new HashSet<Expression>();
        private final TokenLookup tokenLookup;
        private final Expression expression;

        CollectTokens(TokenLookup tokenLookup, Expression expression) {
            this.tokenLookup = tokenLookup;
            this.expression = expression;
        }

        @Override
        public boolean startVisit(Expression expression) {
            return seen.add(expression);
        }

        @Override
        public void visitQuoted(String value) {
            visitUnresolved();
        }

        @Override
        public void visitSystem(String value) {
            visitUnresolved();
        }

        @Override
        public void visitIdentifier(String value) {
            visitTokens(Collections.<Expression>singletonList(new SimpleExpression(value, IncludeType.IDENTIFIER)));
        }

        @Override
        public void visitTokens(List<Expression> tokens) {
            tokenLookup.tokensFor.put(expression, tokens);
        }

        @Override
        public void visitUnresolved() {
            tokenLookup.broken.add(expression);
        }
    }

    private class PathResolvingVisitor implements ExpressionVisitor {
        private final File sourceFile;
        private final BuildableResult results;
        private final Set<Expression> seen = new HashSet<Expression>();

        Set<String> quoted = new HashSet<String>();
        Set<String> system = new HashSet<String>();

        PathResolvingVisitor(File sourceFile, BuildableResult results) {
            this.sourceFile = sourceFile;
            this.results = results;
        }

        @Override
        public boolean startVisit(Expression expression) {
            return seen.add(expression);
        }

        @Override
        public void visitQuoted(String value) {
            if (!quoted.add(value)) {
                return;
            }
            List<File> quotedSearchPath = prependSourceDir(sourceFile, includePaths);
            searchForDependency(quotedSearchPath, value, results);
        }

        @Override
        public void visitSystem(String value) {
            if (!system.add(value)) {
                return;
            }
            searchForDependency(includePaths, value, results);
        }

        @Override
        public void visitIdentifier(String value) {
            results.unresolved();
        }

        @Override
        public void visitTokens(List<Expression> tokens) {
            results.unresolved();
        }

        @Override
        public void visitUnresolved() {
            results.unresolved();
        }
    }
}
