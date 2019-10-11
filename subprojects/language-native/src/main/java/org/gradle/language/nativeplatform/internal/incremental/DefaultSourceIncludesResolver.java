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
import org.gradle.internal.file.FileType;
import org.gradle.internal.hash.HashCode;
import org.gradle.internal.vfs.VirtualFileSystem;
import org.gradle.language.nativeplatform.internal.Expression;
import org.gradle.language.nativeplatform.internal.Include;
import org.gradle.language.nativeplatform.internal.IncludeDirectives;
import org.gradle.language.nativeplatform.internal.IncludeType;
import org.gradle.language.nativeplatform.internal.Macro;
import org.gradle.language.nativeplatform.internal.MacroFunction;
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.ComplexExpression;
import org.gradle.language.nativeplatform.internal.incremental.sourceparser.SimpleExpression;

import javax.annotation.Nullable;
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
    private static final MissingIncludeFile MISSING_INCLUDE_FILE = new MissingIncludeFile();
    private final VirtualFileSystem virtualFileSystem;
    private final Map<File, DirectoryContents> includeRoots = new HashMap<File, DirectoryContents>();
    private final FixedIncludePath includePath;

    public DefaultSourceIncludesResolver(List<File> includePaths, VirtualFileSystem virtualFileSystem) {
        this.virtualFileSystem = virtualFileSystem;
        List<DirectoryContents> includeDirs = new ArrayList<DirectoryContents>(includePaths.size());
        for (File includeDir : includePaths) {
            includeDirs.add(toDir(includeDir));
        }
        this.includePath = new FixedIncludePath(includeDirs);
    }

    @Override
    public IncludeResolutionResult resolveInclude(File sourceFile, Include include, MacroLookup visibleMacros) {
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
            Iterable<Macro> macros = includeDirectives.getMacros(expression.getValue());
            for (Macro macro : macros) {
                found = true;
                resolveExpression(visibleMacros, macro, visitor, tokenLookup);
            }
        }
        if (!found) {
            visitor.visitIdentifier(new SimpleExpression(expression.getValue(), IncludeType.IDENTIFIER));
        }
    }

    private void resolveMacroFunction(MacroLookup visibleMacros, Expression expression, ExpressionVisitor visitor, TokenLookup tokenLookup) {
        boolean found = false;
        for (IncludeDirectives includeDirectives : visibleMacros) {
            Iterable<MacroFunction> macroFunctions = includeDirectives.getMacroFunctions(expression.getValue());
            for (MacroFunction macro : macroFunctions) {
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
        if (!found) {
            visitor.visitUnresolved();
        }
    }

    @Nullable
    @Override
    public IncludeFile resolveInclude(@Nullable File sourceFile, String includePath) {
        IncludePath path = sourceFile != null ? prependSourceDir(sourceFile, this.includePath) : this.includePath;
        return path.searchForDependency(includePath, sourceFile != null);
    }

    private DirectoryContents toDir(File includeDir) {
        DirectoryContents directoryContents = includeRoots.get(includeDir);
        if (directoryContents == null) {
            directoryContents = new DirectoryContents(includeDir);
            includeRoots.put(includeDir, directoryContents);
        }
        return directoryContents;
    }

    private IncludePath prependSourceDir(File sourceFile, FixedIncludePath includePaths) {
        File sourceDir = sourceFile.getParentFile();
        if (includePaths.startsWith(sourceDir)) {
            // Source dir already at the start of the path, just use the include path
            return includePaths;
        }
        return new PrefixedIncludePath(toDir(sourceDir), includePaths);
    }

    private static abstract class IncludePath {
        @Nullable
        abstract IncludeFile searchForDependency(String includePath, boolean quotedPath);
    }

    private static class PrefixedIncludePath extends IncludePath {
        private final DirectoryContents head;
        private final IncludePath tail;

        PrefixedIncludePath(DirectoryContents head, IncludePath tail) {
            this.head = head;
            this.tail = tail;
        }

        @Nullable
        @Override
        IncludeFile searchForDependency(String includePath, boolean quotedPath) {
            CachedIncludeFile includeFile = head.get(includePath);
            if (includeFile.getType() == FileType.RegularFile) {
                return includeFile.toIncludeFile(quotedPath);
            }
            return tail.searchForDependency(includePath, quotedPath);
        }
    }

    private static class FixedIncludePath extends IncludePath {
        private final List<DirectoryContents> directories;
        private final Map<String, CachedIncludeFile> cachedLookups = new HashMap<String, CachedIncludeFile>();

        FixedIncludePath(List<DirectoryContents> directories) {
            this.directories = directories;
        }

        @Nullable
        @Override
        IncludeFile searchForDependency(String includePath, boolean quotedPath) {
            CachedIncludeFile includeFile = cachedLookups.get(includePath);
            if (includeFile == null) {
                for (DirectoryContents dir : directories) {
                    includeFile = dir.get(includePath);
                    if (includeFile.getType() == FileType.RegularFile) {
                        break;
                    }
                }
                if (includeFile == null) {
                    includeFile = MISSING_INCLUDE_FILE;
                }
                cachedLookups.put(includePath, includeFile);
            }
            if (includeFile.getType() == FileType.RegularFile) {
                return includeFile.toIncludeFile(quotedPath);
            }
            return null;
        }

        public boolean startsWith(File sourceDir) {
            return directories.size() > 0 && directories.get(0).searchDir.equals(sourceDir);
        }
    }

    private class DirectoryContents {
        private final File searchDir;
        private final Map<String, CachedIncludeFile> contents = new HashMap<String, CachedIncludeFile>();

        DirectoryContents(File searchDir) {
            this.searchDir = searchDir;
        }

        CachedIncludeFile get(String includePath) {
            CachedIncludeFile includeFile = contents.get(includePath);
            if (includeFile != null) {
                return includeFile;
            }

            File candidate = new File(searchDir, includePath);
            return contents.computeIfAbsent(includePath,
                key -> virtualFileSystem.readRegularFileContentHash(candidate.getAbsolutePath(),
                    contentHash -> (CachedIncludeFile) new SystemIncludeFile(candidate, key, contentHash))
                    .orElse(MISSING_INCLUDE_FILE));
        }
    }

    private static abstract class CachedIncludeFile {
        abstract FileType getType();

        abstract IncludeFile toIncludeFile(boolean quotedPath);
    }

    private static class MissingIncludeFile extends CachedIncludeFile {
        MissingIncludeFile() {
        }

        @Override
        FileType getType() {
            return FileType.Missing;
        }

        @Override
        IncludeFile toIncludeFile(boolean quotedPath) {
            throw new UnsupportedOperationException();
        }
    }

    private static class SystemIncludeFile extends CachedIncludeFile implements IncludeFile {
        final File file;
        final String includePath;
        final HashCode contentHash;

        SystemIncludeFile(File file, String includePath, HashCode contentHash) {
            this.file = file;
            this.includePath = includePath;
            this.contentHash = contentHash;
        }

        @Override
        public String getPath() {
            return includePath;
        }

        @Override
        public boolean isQuotedInclude() {
            return false;
        }

        @Override
        public File getFile() {
            return file;
        }

        @Override
        FileType getType() {
            return FileType.RegularFile;
        }

        @Override
        public HashCode getContentHash() {
            return contentHash;
        }

        @Override
        public boolean equals(Object obj) {
            if (obj == this) {
                return true;
            }
            if (obj == null || getClass() != obj.getClass()) {
                return false;
            }
            SystemIncludeFile other = (SystemIncludeFile) obj;
            return Objects.equal(file, other.file) && contentHash.equals(other.contentHash);
        }

        @Override
        public int hashCode() {
            return contentHash.hashCode();
        }

        @Override
        IncludeFile toIncludeFile(boolean quotedPath) {
            if (quotedPath) {
                return new QuotedIncludeFile(file, includePath, contentHash);
            }
            return this;
        }

        private static class QuotedIncludeFile extends SystemIncludeFile {
            QuotedIncludeFile(File file, String includePath, HashCode contentHash) {
                super(file, includePath, contentHash);
            }

            @Override
            public boolean isQuotedInclude() {
                return true;
            }
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
            IncludePath quotedSearchPath = prependSourceDir(sourceFile, includePath);
            IncludeFile includeFile = quotedSearchPath.searchForDependency(path, true);
            if (includeFile != null) {
                results.resolved(includeFile);
            }
        }

        @Override
        public void visitSystem(Expression value) {
            String path = value.getValue();
            if (!system.add(path)) {
                return;
            }
            IncludeFile includeFile = includePath.searchForDependency(path, false);
            if (includeFile != null) {
                results.resolved(includeFile);
            }
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
