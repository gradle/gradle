/*
 * Copyright 2017 the original author or authors.
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

package org.gradle

import japicmp.cmp.JApiCmpArchive
import japicmp.cmp.JarArchiveComparator
import japicmp.cmp.JarArchiveComparatorOptions
import japicmp.config.Options
import japicmp.model.JApiClass
import japicmp.output.stdout.StdoutOutputGenerator
import org.gradle.api.internal.file.pattern.PathMatcher
import org.gradle.api.internal.file.pattern.PatternMatcherFactory
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.executer.UnderDevelopmentGradleDistribution
import org.gradle.integtests.fixtures.versions.ReleasedVersionDistributions
import org.gradle.util.GradleVersion
import spock.lang.Unroll

import static org.junit.Assume.assumeTrue

@Unroll
class BinaryBreakageIntegrationTest extends AbstractIntegrationSpec{

    private static final List<String> PUBLIC_API_INCLUDES = System.getProperty('org.gradle.public.api.includes').split(':')
    private static final List<String> PUBLIC_API_EXCLUDES = System.getProperty('org.gradle.public.api.excludes').split(':')
    public static final String BREAKING_CHANGE_MARKER = 'BREAKING-CHANGE: '
    public static final String CHANGE_REASON_MARKER = 'REASON: '

    private List<PathMatcher> publicApiIncludeMatchers
    private List<PathMatcher> publicApiExcludeMatchers

    def setup() {
        publicApiIncludeMatchers = PUBLIC_API_INCLUDES.collect { PatternMatcherFactory.compile(true, it) }
        publicApiExcludeMatchers = PUBLIC_API_EXCLUDES.collect { PatternMatcherFactory.compile(true, it) }
        assert !publicApiIncludeMatchers.empty
        assert !publicApiExcludeMatchers.empty
    }

    def "should be binary compatibility with Gradle #version"() {
        GradleDistribution current = new UnderDevelopmentGradleDistribution()

        assumeTrue(previous.version < current.version)

        when:
        def comparison = comparePublicApi(previous, current)
        def acceptedChanges = loadBreakingChangesSince(previous.version)

        then:
        def binaryIncompatibleChanges = comparison.findAll { !it.binaryCompatible }.collectEntries { JApiClass apiClass ->
            [(apiClass.fullyQualifiedName): describeBinaryBreakingChange(apiClass)]
        }
        def remainingChanges = binaryIncompatibleChanges - acceptedChanges
        remainingChanges.values().isEmpty()
        (acceptedChanges - binaryIncompatibleChanges).isEmpty() // Make sure we do not forget to remove fixed binary breakages

        where:
        previous << [new ReleasedVersionDistributions(buildContext).mostRecentFinalRelease]
        version = previous.version.version
    }

    private Map<String, String> loadBreakingChangesSince(GradleVersion previousVersion) {
        def acceptedChangesText = getClass().getResource("/breaking-changes-since-${previousVersion.version}.txt")?.text
        def acceptedChanges = [:]
        if (!acceptedChangesText) {
            return acceptedChanges
        }
        String className = null
        List<String> description = []
        String reason = null
        acceptedChangesText.eachLine { String line ->
            if (line.startsWith(BREAKING_CHANGE_MARKER)) {
                className = line.substring(BREAKING_CHANGE_MARKER.length())
                description = []
                reason = null
            } else if (line.startsWith(CHANGE_REASON_MARKER)) {
                reason = line.substring(CHANGE_REASON_MARKER.length())
            } else if (line.length() == 0 && className && reason) {
                acceptedChanges[className] = description.join('\n')
                className = null
                reason = null
            } else {
                description.add(line)
            }
        }
        if (className && reason && !description.empty) {
            acceptedChanges[className] = description.join('\n')
        }
        return acceptedChanges
    }

    private static String describeBinaryBreakingChange(JApiClass binaryIncompatibleChange) {
        def outputOptions = Options.newDefault()
        outputOptions.outputOnlyBinaryIncompatibleModifications = true
        new StdoutOutputGenerator(outputOptions, [binaryIncompatibleChange]).generate().readLines().tail().join('\n')
    }

    private List<JApiClass> comparePublicApi(GradleDistribution previous, GradleDistribution current) {
        def options = new JarArchiveComparatorOptions()

        def currentJars = getDistributionJars(current)
        def oldJars = getDistributionJars(previous)

        options.classPathMode = JarArchiveComparatorOptions.ClassPathMode.TWO_SEPARATE_CLASSPATHS
        options.newClassPath = currentJars.external*.absolutePath as List
        options.oldClassPath = oldJars.external*.absolutePath as List
        options.ignoreMissingClasses.ignoreAllMissingClasses = true

        def oldGradleJars = oldJars.internal.collect { new JApiCmpArchive(it, previous.version.version) }
        def currentGradleJars = currentJars.internal.collect { new JApiCmpArchive(it, buildContext.version.version) }

        def comparator = new JarArchiveComparator(options)
        def comparison = comparator.compare(oldGradleJars, currentGradleJars)
        comparison.findAll { isPublicApi(it.fullyQualifiedName) }
    }

    private static Map getDistributionJars(GradleDistribution current) {
        def libDir = current.gradleHomeDir.file('lib')
        def allDescendants = libDir.allDescendants()
        def gradleDeps = allDescendants.findAll { it.startsWith('gradle-') }.collect { libDir.file(it) }
        def externalDeps = allDescendants.findAll { !it.startsWith('gradle-') }.collect { libDir.file(it) }
        [
            external: externalDeps,
            internal: gradleDeps
        ]
    }

    boolean isPublicApi(String fullyQualifiedClassname) {
        String[] path = fullyQualifiedClassname.split('\\.')
        return !publicApiExcludeMatchers.any { it.matches(path, 0) } &&
            publicApiIncludeMatchers.any { it.matches(path, 0) }
    }
}
