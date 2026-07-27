/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.tasks.testing.filter;

import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.splitPreserveAllTokens;
import static org.apache.commons.lang3.StringUtils.substringAfterLast;

/**
 * Class-and-method pattern matcher used by {@link TestSelectionMatcher}.
 *
 * <p>Separates three kinds of queries:
 * <ul>
 *   <li><strong>Permissive scan-time check</strong> ({@link #mayIncludeClass(String)}) — may this
 *       class possibly contribute tests that match the includes? Used to prune scanning; false
 *       positives are acceptable, false negatives are not.</li>
 *   <li><strong>Combined test-level check</strong> ({@link #matchesTest(String, String)}) — the
 *       result of {@link #matchesIncludeTest(String, String)} ANDed with the negation of
 *       {@link #matchesExcludeTest(String, String)}. What most callers want for a leaf test.</li>
 *   <li><strong>Separated include / exclude queries</strong>
 *       ({@link #matchesIncludeTest(String, String)} / {@link #matchesExcludeTest(String, String)}
 *       / {@link #matchesExcludeClassExactly(String)}) — lets callers reason about include and exclude
 *       semantics independently.</li>
 * </ul>
 *
 * <p>Pattern interpretation:
 * <ul>
 *   <li>A pattern that starts with an upper-case letter matches the simple class name.</li>
 *   <li>Otherwise, it matches the fully qualified class name.</li>
 *   <li>{@link #matchesExcludeTest(String, String)} applies a heuristic at the class level when
 *       method name is null, so callers that have multiple ways to match against method names
 *       don't prematurly "accept" the test when one of the ways does not match any excludes.</li>
 *   <li>{@link #matchesExcludeClassExactly(String)} does <em>not</em> apply the fuzzy heuristic —
 *       pattern {@code Parent} matches {@code Parent} but not {@code Parent$Nested}.</li>
 * </ul>
 */
@NullMarked
class ClassTestSelectionMatcher {
    private final List<ClassTestPattern> buildScriptIncludePatterns;
    private final List<ClassTestPattern> buildScriptExcludePatterns;
    private final List<ClassTestPattern> commandLineIncludePatterns;

    ClassTestSelectionMatcher(Collection<String> includedTests, Collection<String> excludedTests, Collection<String> includedTestsCommandLine) {
        buildScriptIncludePatterns = prepareClassBasedPatternList(includedTests);
        buildScriptExcludePatterns = prepareClassBasedPatternList(excludedTests);
        commandLineIncludePatterns = prepareClassBasedPatternList(includedTestsCommandLine);
    }

    private static List<ClassTestPattern> prepareClassBasedPatternList(Collection<String> includedTests) {
        return includedTests.stream()
            .map(ClassTestPattern::new)
            .collect(Collectors.toList());
    }

    /**
     * {@return true if the given (className, methodName) pair matches the include patterns and is not excluded by the
     * exclude patterns}
     */
    public boolean matchesTest(String className, @Nullable String methodName) {
        return matchesIncludeTest(className, methodName) && !matchesExcludeTest(className, methodName);
    }

    /**
     * Returns true if the given (className, methodName) pair matches the include patterns.
     *
     * <p>The result is the conjunction of the build-script include patterns and the command-line
     * include patterns. An empty include set is treated as "everything matches" (vacuously true).
     * Exclude patterns are <strong>not</strong> consulted.</p>
     */
    public boolean matchesIncludeTest(String className, @Nullable String methodName) {
        return matchesPattern(buildScriptIncludePatterns, className, methodName)
            && matchesPattern(commandLineIncludePatterns, className, methodName);
    }

    /**
     * Returns true if the given (className, methodName) pair matches any exclude pattern.
     *
     * When methodName is null, this may still return true, even if the className matches any
     * exclude patterns that include a method name.  This is to allow callers that have multiple
     * ways to match method names to avoid "accepting" the test when one of the ways does not
     * match any excludes.
     *
     * <p>An empty exclude set returns false. Include patterns are not consulted.</p>
     */
    public boolean matchesExcludeTest(String className, @Nullable String methodName) {
        return matchesExcludePattern(className, methodName);
    }

    public boolean mayIncludeClass(String fullQualifiedClassName) {
        return mayIncludeClass(buildScriptIncludePatterns, fullQualifiedClassName)
            && mayIncludeClass(commandLineIncludePatterns, fullQualifiedClassName);
    }

    private boolean mayIncludeClass(List<ClassTestPattern> includePatterns, String fullQualifiedName) {
        if (includePatterns.isEmpty()) {
            return true;
        }
        return mayMatchClass(includePatterns, fullQualifiedName);
    }

    private boolean mayMatchClass(List<ClassTestPattern> patterns, String fullQualifiedName) {
        for (ClassTestPattern pattern : patterns) {
            if (pattern.mayIncludeClass(fullQualifiedName)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPattern(List<ClassTestPattern> includePatterns, String className, @Nullable String methodName) {
        if (includePatterns.isEmpty()) {
            return true;
        }
        return matchesClassAndMethod(includePatterns, className, methodName);
    }

    private boolean matchesExcludePattern(String className, @Nullable String methodName) {
        if (buildScriptExcludePatterns.isEmpty()) {
            return false;
        }
        if (mayMatchClass(buildScriptExcludePatterns, className) && methodName == null) {
            // When there is a class name match, return true for excluding it so that we can keep
            // searching in individual test methods for an exact match. If we return false here
            // instead, then we'll give up searching individual test methods and just ignore the
            // entire test class, which may not be what we want.
            return true;
        }
        return matchesClassAndMethod(buildScriptExcludePatterns, className, methodName);
    }

    /**
     * Returns true if the class name exactly matches an exclude pattern's class component.
     *
     * <p>Unlike {@link #matchesExcludeTest(String, String)}, this does <em>not</em> consult
     * the fuzzy {@code mayMatchClass} heuristic: pattern {@code Parent} does not match
     * class {@code Parent$Nested} here.</p>
     */
    public boolean matchesExcludeClassExactly(String className) {
        for (ClassTestPattern pattern : buildScriptExcludePatterns) {
            if (pattern.matchesClass(className)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesClassAndMethod(List<ClassTestPattern> patterns, String className, @Nullable String methodName) {
        for (ClassTestPattern pattern : patterns) {
            if (pattern.matchesClassAndMethod(className, methodName)) {
                return true;
            }
            if (pattern.matchesClass(className)) {
                return true;
            }
        }
        return false;
    }

    private static final class ClassTestPattern {
        private final Pattern pattern;
        private String[] segments;
        private LastElementMatcher lastElementMatcher;
        private final ClassNameSelector classNameSelector;

        private ClassTestPattern(String pattern) {
            this.pattern = preparePattern(pattern);
            this.classNameSelector = patternStartsWithUpperCase(pattern) ?
                new SimpleClassNameSelector() : new FullQualifiedClassNameSelector();
            int firstWildcardIndex = pattern.indexOf('*');
            //https://github.com/gradle/gradle/issues/27572
            int firstParametrizeIndex = pattern.indexOf('[');
            if (firstWildcardIndex == -1) {
                segments = splitPreserveAllTokens(pattern, '.');
                if(firstParametrizeIndex == -1){
                    lastElementMatcher = new NoWildcardMatcher();
                }else{
                    segments = splitPreserveAllTokens(pattern.substring(0, firstParametrizeIndex), '.');
                    segments[segments.length-1] += pattern.substring(firstParametrizeIndex);
                }
            } else {
                segments = splitPreserveAllTokens(pattern.substring(0, firstWildcardIndex), '.');
                lastElementMatcher = new WildcardMatcher();
            }
        }

        private static Pattern preparePattern(String input) {
            StringBuilder pattern = new StringBuilder();
            String[] split = StringUtils.splitPreserveAllTokens(input, '*');
            for (String s : split) {
                if (s.isEmpty()) {
                    pattern.append(".*"); //replace wildcard '*' with '.*'
                } else {
                    if (pattern.length() > 0) {
                        pattern.append(".*"); //replace wildcard '*' with '.*'
                    }
                    pattern.append(Pattern.quote(s)); //quote everything else
                }
            }
            return Pattern.compile(pattern.toString());
        }

        private boolean mayIncludeClass(String fullQualifiedName) {
            if (patternStartsWithWildcard()) {
                return true;
            }
            String[] classNameArray =
                classNameSelector.determineTargetClassName(fullQualifiedName).split("\\.");
            if (classNameIsShorterThanPattern(classNameArray)) {
                return false;
            }
            for (int i = 0; i < segments.length; ++i) {
                if (lastClassNameElementMatchesPenultimatePatternElement(classNameArray, i)) {
                    return true;
                } else if (lastClassNameElementMatchesLastPatternElement(classNameArray, i)) {
                    return true;
                } else if (!classNameArray[i].equals(segments[i])) {
                    return false;
                }
            }
            return false;
        }

        private boolean matchesClass(String fullQualifiedName) {
            return pattern.matcher(classNameSelector.determineTargetClassName(fullQualifiedName)).matches();
        }

        private boolean matchesClassAndMethod(String fullQualifiedName, @Nullable String methodName) {
            if (methodName == null) {
                return false;
            }
            return pattern.matcher(classNameSelector.determineTargetClassName(fullQualifiedName) + "." + methodName).matches();
        }

        private boolean lastClassNameElementMatchesPenultimatePatternElement(String[] className, int index) {
            return index == segments.length - 2 && index == className.length - 1 && classNameMatch(className[index], segments[index]);
        }

        private boolean lastClassNameElementMatchesLastPatternElement(String[] className,
                                                                      int index) {
            return index == segments.length - 1 && lastElementMatcher.match(className[index],
                segments[index]);
        }

        private boolean patternStartsWithWildcard() {
            return segments.length == 0;
        }

        private boolean classNameIsShorterThanPattern(String[] classNameArray) {
            return classNameArray.length < segments.length - 1;
        }

        private boolean patternStartsWithUpperCase(String pattern) {
            return !pattern.isEmpty() && Character.isUpperCase(pattern.charAt(0));
        }
    }

    private static String getSimpleName(String fullQualifiedName) {
        String simpleName = substringAfterLast(fullQualifiedName, ".");
        if ("".equals(simpleName)) {
            return fullQualifiedName;
        }
        return simpleName;
    }

    // Foo can match both Foo and Foo$NestedClass
    // https://github.com/gradle/gradle/issues/5763
    private static boolean classNameMatch(String simpleClassName, String patternSimpleClassName) {
        if (simpleClassName.equals(patternSimpleClassName)) {
            return true;
        } else if (patternSimpleClassName.contains("$")) {
            return simpleClassName.equals(patternSimpleClassName.substring(0, patternSimpleClassName.indexOf('$')));
        } else {
            return false;
        }
    }

    private interface LastElementMatcher {
        boolean match(String classElement, String patternElement);
    }

    private static class NoWildcardMatcher implements LastElementMatcher {
        @Override
        public boolean match(String classElement, String patternElement) {
            return classNameMatch(classElement, patternElement);
        }
    }

    private static class WildcardMatcher implements LastElementMatcher {
        @Override
        public boolean match(String classElement, String patternElement) {
            return classElement.startsWith(patternElement) || classNameMatch(classElement, patternElement);
        }
    }

    private interface ClassNameSelector {
        String determineTargetClassName(String fullQualifiedName);
    }

    private static class FullQualifiedClassNameSelector implements ClassNameSelector {
        @Override
        public String determineTargetClassName(String fullQualifiedName) {
            return fullQualifiedName;
        }
    }

    private static class SimpleClassNameSelector implements ClassNameSelector {
        @Override
        public String determineTargetClassName(String fullQualifiedName) {
            return getSimpleName(fullQualifiedName);
        }
    }
}
