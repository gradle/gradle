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
package org.gradle.api.internal.tasks.testing.filter;


import org.apache.commons.lang3.StringUtils;
import org.gradle.util.internal.TextUtil;
import org.jspecify.annotations.NullMarked;

import java.nio.file.Path;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import java.util.stream.Collectors;

import static org.apache.commons.lang3.StringUtils.splitPreserveAllTokens;
import static org.apache.commons.lang3.StringUtils.substringAfterLast;

/**
 * This class has two public APIs:
 *
 * <ul>
 * <li>Judge whether a test class might be included. For example, class 'org.gradle.Test' can't
 * be included by pattern 'org.apache.Test'
 * <li>Judge whether a test method is matched exactly.
 * </ul>
 *
 * In both cases, if the pattern starts with an upper-case letter, it will be used to match
 * the simple class name;
 * otherwise, it will be used to match the fully qualified class name.
 */
public class TestSelectionMatcher {
    private final List<ClassTestPattern> buildScriptIncludePatterns;
    private final List<ClassTestPattern> buildScriptExcludePatterns;
    private final List<ClassTestPattern> commandLineIncludePatterns;

    private final List<FileTestPattern> pathBuildScriptIncludePatterns;
    private final List<FileTestPattern> pathBuildScriptExcludePatterns;
    private final List<FileTestPattern> pathCommandLineIncludePatterns;

    public TestSelectionMatcher(TestFilterSpec filter) {
        buildScriptIncludePatterns = prepareClassBasedPatternList(filter.getIncludedTests());
        buildScriptExcludePatterns = prepareClassBasedPatternList(filter.getExcludedTests());
        commandLineIncludePatterns = prepareClassBasedPatternList(filter.getIncludedTestsCommandLine());

        pathBuildScriptIncludePatterns = preparePathBasedPatternList(filter.getIncludedTests());
        pathBuildScriptExcludePatterns = preparePathBasedPatternList(filter.getExcludedTests());
        pathCommandLineIncludePatterns = preparePathBasedPatternList(filter.getIncludedTestsCommandLine());
    }

    private static List<ClassTestPattern> prepareClassBasedPatternList(Collection<String> includedTests) {
        return preparePatternList(includedTests, TestSelectionMatcher::isClassBasedPattern, ClassTestPattern::new);
    }

    private static List<FileTestPattern> preparePathBasedPatternList(Collection<String> includedTests) {
        return preparePatternList(includedTests, TestSelectionMatcher::isPathBasedPattern, FileTestPattern::new);
    }

    private static <T> List<T> preparePatternList(Collection<String> includedTests, Predicate<String> patternFilter, Function<String, T> patternCreator) {
        return includedTests.stream()
            .filter(patternFilter)
            .map(patternCreator)
            .collect(Collectors.toList());
    }

    private static boolean isClassBasedPattern(String pattern) {
        return !isPathBasedPattern(pattern);
    }

    private static boolean isPathBasedPattern(String pattern) {
        return pattern.contains("/"); // Only Unix-style paths are supported in test selection patterns
    }

    public boolean hasClassBasedFilters() {
        return !buildScriptIncludePatterns.isEmpty() || !buildScriptExcludePatterns.isEmpty() || !commandLineIncludePatterns.isEmpty();
    }

    public boolean hasPathBasedFilters() {
        return !pathBuildScriptIncludePatterns.isEmpty() || !pathBuildScriptExcludePatterns.isEmpty() || !pathCommandLineIncludePatterns.isEmpty();
    }

    public boolean matchesPath(Path path) {
        return isIncludedPath(path) && !isExcludedPath(path);
    }

    private boolean isIncludedPath(Path path) {
        boolean isImplicitlyIncluded = pathBuildScriptIncludePatterns.isEmpty() && pathCommandLineIncludePatterns.isEmpty();
        return isImplicitlyIncluded || matchesPattern(pathBuildScriptIncludePatterns, path) || matchesPattern(pathCommandLineIncludePatterns, path);
    }

    private boolean isExcludedPath(Path path) {
        if (pathBuildScriptExcludePatterns.isEmpty()) {
            return false;
        }
        return matchesPattern(pathBuildScriptExcludePatterns, path);
    }

    private boolean matchesPattern(List<FileTestPattern> patterns, Path path) {
        for (FileTestPattern pattern : patterns) {
            String normalizedPath = TextUtil.normaliseFileSeparators(path.toString());
            if (pattern.matches("/" + normalizedPath)) { // Add leading slash in target path (will always be optionally present at the start of the regex)
                return true;
            }
        }
        return false;
    }

    public boolean matchesTest(String className, String methodName) {
        return matchesPattern(buildScriptIncludePatterns, className, methodName)
            && matchesPattern(commandLineIncludePatterns, className, methodName)
            && !matchesExcludePattern(className, methodName);
    }

    public boolean mayIncludeClass(String fullQualifiedClassName) {
        return mayIncludeClass(buildScriptIncludePatterns, fullQualifiedClassName)
            && mayIncludeClass(commandLineIncludePatterns, fullQualifiedClassName)
            && !mayExcludeClass(fullQualifiedClassName);
    }

    private boolean mayIncludeClass(List<ClassTestPattern> includePatterns, String fullQualifiedName) {
        if (includePatterns.isEmpty()) {
            return true;
        }
        return mayMatchClass(includePatterns, fullQualifiedName);
    }

    public boolean mayExcludeClass(String fullQualifiedName) {
        if (buildScriptExcludePatterns.isEmpty()) {
            return false;
        }
        return matchesClass(buildScriptExcludePatterns, fullQualifiedName);
    }

    private boolean matchesClass(List<ClassTestPattern> patterns, String fullQualifiedName) {
        for (ClassTestPattern pattern : patterns) {
            if (pattern.matchesClass(fullQualifiedName)) {
                return true;
            }
        }
        return false;
    }

    private boolean mayMatchClass(List<ClassTestPattern> patterns, String fullQualifiedName) {
        for (ClassTestPattern pattern : patterns) {
            if (pattern.mayIncludeClass(fullQualifiedName)) {
                return true;
            }
        }
        return false;
    }

    private boolean matchesPattern(List<ClassTestPattern> includePatterns, String className, String methodName) {
        if (includePatterns.isEmpty()) {
            return true;
        }
        return matchesClassAndMethod(includePatterns, className, methodName);
    }

    private boolean matchesExcludePattern(String className, String methodName) {
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

    private boolean matchesClassAndMethod(List<ClassTestPattern> patterns, String className, String methodName) {
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

    @NullMarked
    private static final class FileTestPattern {
        private final Pattern pattern;

        private FileTestPattern(String path) {
            pattern = preparePattern(path);
        }

        private boolean matches(String input) {
            return pattern.matcher(input).matches();
        }

        private static Pattern preparePattern(String input) {
            try {
                // Add optional leading slash to match both "absolute" and "relative" paths (all paths are treated as relative to project root)
                return Pattern.compile("/?(" + TextUtil.normaliseFileSeparators(input) + ")");
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Path filter pattern is not a valid regex: " + input, e);
            }
        }
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

        private boolean matchesClassAndMethod(String fullQualifiedName, String methodName) {
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
