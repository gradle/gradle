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

import com.google.common.base.Objects;
import org.gradle.api.internal.changedetection.state.FileSnapshot;
import org.gradle.api.internal.changedetection.state.FileSystemSnapshotter;
import org.gradle.internal.file.FileType;
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
    private final FileSystemSnapshotter fileSystemSnapshotter;
    private final Map<File, Map<String, IncludeFileImpl>> includeRoots;

    public DefaultSourceIncludesResolver(List<File> includePaths, FileSystemSnapshotter fileSystemSnapshotter) {
        this.includePaths = includePaths;
        this.fileSystemSnapshotter = fileSystemSnapshotter;
        this.includeRoots = new HashMap<File, Map<String, IncludeFileImpl>>();
    }

    @Override
    public IncludeResolutionResult resolveInclude(final File sourceFile, Include include, MacroLookup visibleMacros) {
        BuildableResult results = new BuildableResult();
        resolveExpression(visibleMacros, include, new PathResolvingVisitor(sourceFile, results), new TokenLookup());
        return results;
    }

    private void resolveExpression(MacroLookup visibleMacros, Expression expression, ExpressionVisitor visitor, TokenLookup tokenLookup) {
        if (expression.getType() == IncludeType.SYSTEM) {
            visitor.visitSystem(expression);
        } else if (expression.getType() == IncludeType.QUOTED) {
            visitor.visitQuoted(expression);
        } else if (expression.getType() == IncludeType.IDENTIFIER) {
            visitor.visitIdentifier(expression);
        } else if (expression.getType() == IncludeType.ARGS_LIST) {
            visitor.visitTokens(expression);
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
            } else if (expression.getType() == IncludeType.EXPAND_TOKEN_CONCATENATION) {
                resolveAndExpandTokenConcatenation(visibleMacros, expression, visitor, tokenLookup);
            } else if (expression.getType() == IncludeType.EXPRESSIONS) {
                resolveExpressionSequence(visibleMacros, expression, visitor, tokenLookup);
            } else {
                visitor.visitUnresolved();
            }
        }
    }

    private void resolveExpressionSequence(MacroLookup visibleMacros, Expression expression, ExpressionVisitor visitor, TokenLookup tokenLookup) {
        List<Expression> expressions = expression.getArguments();
        // Only <function-call>+ <args-list> supported
        if (expressions.size() < 2) {
            visitor.visitUnresolved();
            return;
        }
        Expression argListExpression = expressions.get(expressions.size() - 1);
        List<Expression> headExpressions = expressions.subList(0, expressions.size() - 1);
        Collection<Expression> args = resolveExpressionToTokens(visibleMacros, argListExpression, visitor, tokenLookup);
        for (Expression value : args) {
            resolveExpressionSequenceForArgs(visibleMacros, headExpressions, value, visitor, tokenLookup);
        }
    }

    private void resolveExpressionSequenceForArgs(MacroLookup visibleMacros, List<Expression> expressions, Expression args, ExpressionVisitor visitor, TokenLookup tokenLookup) {
        if (args.getType() != IncludeType.ARGS_LIST) {
            visitor.visitUnresolved();
            return;
        }

        Expression macroFunctionExpression = expressions.get(expressions.size() - 1);
        List<Expression> headExpressions = expressions.subList(0, expressions.size() - 1);
        Collection<Expression> identifiers = resolveExpressionToTokens(visibleMacros, macroFunctionExpression, visitor, tokenLookup);
        for (Expression value : identifiers) {
            if (value.getType() != IncludeType.IDENTIFIER) {
                visitor.visitUnresolved();
                continue;
            }
            ComplexExpression macroExpression = new ComplexExpression(IncludeType.MACRO_FUNCTION, value.getValue(), args.getArguments());
            if (headExpressions.isEmpty()) {
                resolveExpression(visibleMacros, macroExpression, visitor, tokenLookup);
                return;
            }
            Collection<Expression> resolved = resolveExpressionToTokens(visibleMacros, macroExpression, visitor, tokenLookup);
            for (Expression newArgs : resolved) {
                resolveExpressionSequenceForArgs(visibleMacros, headExpressions, newArgs, visitor, tokenLookup);
            }
        }
    }

    private void resolveTokenConcatenation(MacroLookup visibleMacros, Expression expression, ExpressionVisitor visitor, TokenLookup tokenLookup) {
        Collection<Expression> expressions = resolveTokenConcatenationToTokens(visibleMacros, expression, visitor, tokenLookup);
        for (Expression concatExpression : expressions) {
            resolveExpression(visibleMacros, concatExpression, visitor, tokenLookup);
        }
    }

    private void resolveAndExpandTokenConcatenation(MacroLookup visibleMacros, Expression expression, ExpressionVisitor visitor, TokenLookup tokenLookup) {
        Collection<Expression> expressions = resolveTokenConcatenationToTokens(visibleMacros, expression, visitor, tokenLookup);
        for (Expression concatExpression : expressions) {
            resolveExpression(visibleMacros, concatExpression.asMacroExpansion(), visitor, tokenLookup);
        }
    }

    /**
     * Resolves the given expression to zero or more expressions that have been macro expanded and token concatenated.
     */
    private Collection<Expression> resolveExpressionToTokens(MacroLookup visibleMacros, Expression expression, ExpressionVisitor visitor, TokenLookup tokenLookup) {
        if (expression.getType() == IncludeType.TOKEN_CONCATENATION) {
            return resolveTokenConcatenationToTokens(visibleMacros, expression, visitor, tokenLookup);
        }
        if (expression.getType() != IncludeType.MACRO && expression.getType() != IncludeType.MACRO_FUNCTION && expression.getType() != IncludeType.EXPAND_TOKEN_CONCATENATION) {
            return Collections.singletonList(expression);
        }

        // Otherwise, macro or macro function call
        if (!tokenLookup.hasTokensFor(expression)) {
            resolveExpression(visibleMacros, expression, new CollectTokens(tokenLookup, expression), tokenLookup);
        }
        if (tokenLookup.isUnresolved(expression)) {
            visitor.visitUnresolved();
        }
        return tokenLookup.tokensFor(expression);
    }

    private Collection<Expression> resolveTokenConcatenationToTokens(MacroLookup visibleMacros, Expression expression, ExpressionVisitor visitor, TokenLookup tokenLookup) {
        Expression left = expression.getArguments().get(0);
        Expression right = expression.getArguments().get(1);

        Collection<Expression> leftValues = resolveExpressionToTokens(visibleMacros, left, visitor, tokenLookup);
        Collection<Expression> rightValues = resolveExpressionToTokens(visibleMacros, right, visitor, tokenLookup);
        if (leftValues.isEmpty() || rightValues.isEmpty()) {
            return Collections.emptyList();
        }

        List<Expression> expressions = new ArrayList<Expression>(leftValues.size() * rightValues.size());
        for (Expression leftValue : leftValues) {
            if (leftValue.getType() != IncludeType.IDENTIFIER) {
                if (rightValues.size() == 1) {
                    Expression rightValue = rightValues.iterator().next();
                    if (rightValue.getType() == IncludeType.EXPRESSIONS && rightValue.getArguments().isEmpty()) {
                        // Empty RHS
                        expressions.add(leftValue);
                        continue;
                    }
                }
                // Not supported for now
                visitor.visitUnresolved();
                continue;
            }
            String leftString = leftValue.getValue();
            for (Expression rightValue : rightValues) {
                // Handle just empty string, single identifier or '(' params? ')', should handle more by parsing the tokens into an expression
                if (rightValue.getType() == IncludeType.IDENTIFIER) {
                    expressions.add(new SimpleExpression(leftString + rightValue.getValue(), IncludeType.IDENTIFIER));
                    continue;
                }
                if (rightValue.getType() == IncludeType.ARGS_LIST) {
                    expressions.add(new ComplexExpression(IncludeType.MACRO_FUNCTION, leftString, rightValue.getArguments()));
                    continue;
                }
                if (rightValue.getType() == IncludeType.EXPRESSIONS && rightValue.getArguments().isEmpty()) {
                    expressions.add(new SimpleExpression(leftString, IncludeType.IDENTIFIER));
                    continue;
                }
                visitor.visitUnresolved();
            }
        }
        return expressions;
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
            visitor.visitIdentifier(new SimpleExpression(expression.getValue(), IncludeType.IDENTIFIER));
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
                        arguments = Collections.singletonList(SimpleExpression.EMPTY_EXPRESSIONS);
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
        File sourceDir = sourceFile.getParentFile();
        if (includePaths.size() > 1 && includePaths.get(0).equals(sourceDir)) {
            // Source dir already at the start of the path, just use the include path
            return includePaths;
        }
        List<File> quotedSearchPath = new ArrayList<File>(includePaths.size() + 1);
        quotedSearchPath.add(sourceDir);
        quotedSearchPath.addAll(includePaths);
        return quotedSearchPath;
    }

    private void searchForDependency(List<File> searchPath, String include, BuildableResult dependencies) {
        for (File searchDir : searchPath) {
            Map<String, IncludeFileImpl> searchedIncludes = includeRoots.get(searchDir);
            if (searchedIncludes == null) {
                searchedIncludes = new HashMap<String, IncludeFileImpl>();
                includeRoots.put(searchDir, searchedIncludes);
            }
            if (searchedIncludes.containsKey(include)) {
                IncludeFileImpl includeFile = searchedIncludes.get(include);
                if (includeFile.snapshot.getType() == FileType.RegularFile) {
                    dependencies.resolved(includeFile);
                    return;
                }
                continue;
            }

            File candidate = new File(searchDir, include);
            FileSnapshot fileSnapshot = fileSystemSnapshotter.snapshotSelf(candidate);
            IncludeFileImpl includeFile = fileSnapshot.getType() == FileType.RegularFile ? new IncludeFileImpl(candidate, fileSnapshot) : new IncludeFileImpl(null, fileSnapshot);
            searchedIncludes.put(include, includeFile);

            if (fileSnapshot.getType() == FileType.RegularFile) {
                dependencies.resolved(includeFile);
                return;
            }
        }
    }

    private static class IncludeFileImpl implements IncludeFile {
        final File file;
        final FileSnapshot snapshot;

        IncludeFileImpl(File file, FileSnapshot snapshot) {
            this.file = file;
            this.snapshot = snapshot;
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        public FileSnapshot getSnapshot() {
            return snapshot;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            IncludeFileImpl other = (IncludeFileImpl) obj;
            return Objects.equal(file, other.file) && snapshot.equals(other.snapshot);
        }

        @Override
        public int hashCode() {
            return snapshot.hashCode();
        }
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
        void visitQuoted(Expression value);

        /**
         * Called when an expression resolves to a system path.
         */
        void visitSystem(Expression value);

        /**
         * Called when an expression resolves to a single identifier that could not be macro expanded.
         */
        void visitIdentifier(Expression value);

        /**
         * Called when an expression resolves to zero or more tokens.
         */
        void visitTokens(Expression tokens);

        /**
         * Called when an expression could not be resolved to a value.
         */
        void visitUnresolved();
    }

    private static class BuildableResult implements IncludeResolutionResult {
        private final Set<IncludeFile> files = new LinkedHashSet<IncludeFile>();
        private boolean missing;

        void resolved(IncludeFile includeFile) {
            files.add(includeFile);
        }

        void unresolved() {
            missing = true;
        }

        @Override
        public boolean isComplete() {
            return !missing;
        }

        @Override
        public Set<IncludeFile> getFiles() {
            return files;
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
        public void visitQuoted(Expression value) {
            visitTokens(value);
        }

        @Override
        public void visitSystem(Expression value) {
            visitTokens(value);
        }

        @Override
        public void visitIdentifier(Expression value) {
            visitTokens(value);
        }

        @Override
        public void visitTokens(Expression tokens) {
            tokenLookup.addTokensFor(expression, tokens);
        }

        @Override
        public void visitUnresolved() {
            tokenLookup.unresolved(expression);
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
        public void visitQuoted(Expression value) {
            String path = value.getValue();
            if (!quoted.add(path)) {
                return;
            }
            List<File> quotedSearchPath = prependSourceDir(sourceFile, includePaths);
            searchForDependency(quotedSearchPath, path, results);
        }

        @Override
        public void visitSystem(Expression value) {
            String path = value.getValue();
            if (!system.add(path)) {
                return;
            }
            searchForDependency(includePaths, path, results);
        }

        @Override
        public void visitIdentifier(Expression value) {
            results.unresolved();
        }

        @Override
        public void visitTokens(Expression tokens) {
            results.unresolved();
        }

        @Override
        public void visitUnresolved() {
            results.unresolved();
        }
    }
}
