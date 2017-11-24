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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        resolveExpression(visibleMacros, include, new PathResolvingVisitor(sourceFile, results));
        return results;
    }

    private void resolveExpression(MacroLookup visibleMacros, Expression expression, ExpressionVisitor visitor) {
        if (expression.getType() == IncludeType.SYSTEM) {
            visitor.visitSystem(expression.getValue());
        } else if (expression.getType() == IncludeType.QUOTED) {
            visitor.visitQuoted(expression.getValue());
        } else if (expression.getType() == IncludeType.MACRO) {
            resolveMacro(visibleMacros, expression, visitor);
        } else if (expression.getType() == IncludeType.MACRO_FUNCTION) {
            resolveMacroFunction(visibleMacros, expression, visitor);
        } else if (expression.getType() == IncludeType.TOKEN_CONCATENATION) {
            resolveTokenConcatenation(visibleMacros, expression, visitor);
        } else if (expression.getType() == IncludeType.IDENTIFIER) {
            visitor.visitToken(expression.getValue());
        } else if (expression.getType() == IncludeType.TOKENS) {
            visitor.visitTokens(expression.getArguments());
        } else {
            visitor.visitUnresolved(expression);
        }
    }

    private void resolveTokenConcatenation(final MacroLookup visibleMacros, final Expression expression, final ExpressionVisitor visitor) {
        final Expression left = expression.getArguments().get(0);
        final Expression right = expression.getArguments().get(1);
        resolveExpression(visibleMacros, left, new ExpressionVisitor() {
            @Override
            public void visitQuoted(String value) {
                visitor.visitUnresolved(expression);
            }

            @Override
            public void visitSystem(String value) {
                visitor.visitUnresolved(expression);
            }

            @Override
            public void visitTokens(List<Expression> tokens) {
                visitor.visitUnresolved(expression);
            }

            @Override
            public void visitToken(final String leftValue) {
                resolveExpression(visibleMacros, right, new ExpressionVisitor() {
                    @Override
                    public void visitQuoted(String value) {
                        visitor.visitUnresolved(expression);
                    }

                    @Override
                    public void visitSystem(String value) {
                        visitor.visitUnresolved(expression);
                    }

                    @Override
                    public void visitTokens(List<Expression> tokens) {
                        // Handle just '(' expression ')', should handle more
                        if (tokens.size() == 3 && "(".equals(tokens.get(0).getValue()) && ")".equals(tokens.get(2).getValue())) {
                            resolveExpression(visibleMacros, new ComplexExpression(IncludeType.MACRO_FUNCTION, leftValue, Collections.singletonList(tokens.get(1))), visitor);
                        }
                    }

                    @Override
                    public void visitToken(String value) {
                        String newValue = leftValue + value;
                        resolveExpression(visibleMacros, new SimpleExpression(newValue, IncludeType.MACRO), visitor);
                    }

                    @Override
                    public void visitUnresolved(Expression expression) {
                        if (expression.getType() == IncludeType.MACRO) {
                            visitToken(expression.getValue());
                        } else {
                            visitor.visitUnresolved(expression);
                        }
                    }
                });
            }

            @Override
            public void visitUnresolved(Expression expression) {
                if (expression.getType() == IncludeType.MACRO) {
                    visitToken(expression.getValue());
                } else {
                    visitor.visitUnresolved(expression);
                }
            }
        });
    }

    private void resolveMacro(MacroLookup visibleMacros, Expression expression, ExpressionVisitor visitor) {
        boolean found = false;
        for (IncludeDirectives includeDirectives : visibleMacros) {
            for (Macro macro : includeDirectives.getMacros()) {
                if (expression.getValue().equals(macro.getName())) {
                    found = true;
                    resolveExpression(visibleMacros, macro, visitor);
                }
            }
        }
        if (!found) {
            visitor.visitToken(expression.getValue());
        }
    }

    private void resolveMacroFunction(MacroLookup visibleMacros, Expression expression, ExpressionVisitor visitor) {
        boolean found = false;
        for (IncludeDirectives includeDirectives : visibleMacros) {
            for (MacroFunction macro : includeDirectives.getMacrosFunctions()) {
                // Currently only handle functions with no parameters
                if (expression.getValue().equals(macro.getName()) && macro.getParameterCount() == expression.getArguments().size()) {
                    found = true;
                    Expression result = macro.evaluate(expression.getArguments());
                    resolveExpression(visibleMacros, result, visitor);
                }
            }
        }
        if (!found) {
            visitor.visitUnresolved(expression);
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

    private interface ExpressionVisitor {
        void visitQuoted(String value);

        void visitSystem(String value);

        void visitToken(String value);

        void visitTokens(List<Expression> tokens);

        void visitUnresolved(Expression expression);
    }

    private static class BuildableResult implements IncludeResolutionResult {
        private final List<File> files = new ArrayList<File>();
        private final List<File> candidates = new ArrayList<File>();
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
        public List<File> getFiles() {
            return files;
        }

        @Override
        public List<File> getCheckedLocations() {
            return candidates;
        }
    }

    private class PathResolvingVisitor implements ExpressionVisitor {
        private final File sourceFile;
        private final BuildableResult results;

        PathResolvingVisitor(File sourceFile, BuildableResult results) {
            this.sourceFile = sourceFile;
            this.results = results;
        }

        @Override
        public void visitQuoted(String value) {
            List<File> quotedSearchPath = prependSourceDir(sourceFile, includePaths);
            searchForDependency(quotedSearchPath, value, results);
        }

        @Override
        public void visitSystem(String value) {
            searchForDependency(includePaths, value, results);
        }

        @Override
        public void visitToken(String value) {
            results.unresolved();
        }

        @Override
        public void visitTokens(List<Expression> tokens) {
            results.unresolved();
        }

        @Override
        public void visitUnresolved(Expression expression) {
            results.unresolved();
        }
    }
}
