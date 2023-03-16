/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.test.fixtures.junitplatform

import groovy.io.FileType

import java.util.regex.Pattern

class JUnitPlatformTestRewriter {

    private static final Map REPLACEMENTS = Collections.unmodifiableMap([
        'org.junit.Test': 'org.junit.jupiter.api.Test',
        'org.junit.Before;': 'org.junit.jupiter.api.BeforeEach;',
        'org.junit.After;': 'org.junit.jupiter.api.AfterEach;',
        '@org.junit.Before ': '@org.junit.jupiter.api.BeforeEach ',
        '@org.junit.After ': '@org.junit.jupiter.api.AfterEach ',
        '@org.junit.Before\n': '@org.junit.jupiter.api.BeforeEach\n',
        '@org.junit.After\n': '@org.junit.jupiter.api.AfterEach\n',
        'org.junit.BeforeClass': 'org.junit.jupiter.api.BeforeAll',
        'org.junit.AfterClass': 'org.junit.jupiter.api.AfterAll',
        'org.junit.Ignore': 'org.junit.jupiter.api.Disabled',
        '@Before\n': '@BeforeEach\n',
        '@After\n': '@AfterEach\n',
        '@Before ': '@BeforeEach ',
        '@After ': '@AfterEach ',
        '@BeforeClass': '@BeforeAll',
        '@AfterClass': '@AfterAll',
        '@Ignore': '@Disabled',
        'import org.junit.*': 'import org.junit.jupiter.api.*',
        'org.junit.Assume': 'org.junit.jupiter.api.Assumptions',
        'org.junit.Assert': 'org.junit.jupiter.api.Assertions',
        'junit.framework.Assert': 'org.junit.jupiter.api.Assertions',
        'Assert.': 'Assertions.',
        'Assume.': 'Assumptions.',
        'org.junit.experimental.categories.Category': 'org.junit.jupiter.api.Tag'
    ])

    private static final List<Closure> TRANSFORMS = Collections.unmodifiableList([
        { String string ->
            // This transform translates '@Category(...)' lines into one or more '@Tag()' lines using the name of the category class as the tag
            // For example:
            // @Category(org.gradle.Foo.class) -> @Tag("org.gradle.Foo")
            // @Category({org.gradle.Foo.class, org.gradle.Bar.class}) -> @Tag("org.gradle.Foo")\n@Tag("org.gradle.Bar")

            // First, capture the list of categories inside the category tag, which may be a single category
            // or a list of categories inside '{...}'
            StringBuilder result = new StringBuilder()
            Pattern categoryTagPattern = Pattern.compile('@Category\\(\\{?([^}]*)}?\\)\n')
            def matcher = categoryTagPattern.matcher(string)

            // Then, replace each category class with an @Tag() annotation using the class name as the tag
            int previous = 0
            while (matcher.find()) {
                def categories = matcher.group(1)
                // prepend everything from the end of the previous @Category match until the beginning of the current @Category match
                result.append(string, previous, matcher.start())
                // append the transformed @Tag annotations
                result.append(categories.replaceAll(',?\\s*([^,\\s]+)\\.class', '@Tag("$1")\n'))
                previous = matcher.end()
            }
            // append everything after the last @Category match
            result.append(string, previous, string.length())
            return result.toString()
        }
    ])

    static rewriteWithJupiter(File projectDir, String dependencyVersion) {
        replaceCategoriesWithTagsInBuildFile(projectDir)
        rewriteBuildFileWithJupiter(projectDir, dependencyVersion)
        rewriteJavaFilesWithJupiterAnno(projectDir)
        rewriteJavaModuleFileWithJupiterRequires(projectDir)
    }

    static replaceCategoriesWithTagsInBuildFile(File projectDir) {
        // http://junit.org/junit5/docs/current/user-guide/#migrating-from-junit4-categories-support
        File buildFile = new File(projectDir, 'build.gradle')
        if (buildFile.exists()) {
            String text = buildFile.text
            text = text.replace('excludeCategories', 'excludeTags')
            text = text.replace('includeCategories', 'includeTags')
            buildFile.text = text
        }
    }

    static rewriteWithVintage(File projectDir, String dependencyVersion) {
        replaceCategoriesWithTagsInBuildFile(projectDir)
        rewriteBuildFileWithVintage(projectDir, dependencyVersion)
    }

    static rewriteBuildFileWithJupiter(File buildFile, String dependencyVersion) {
        rewriteBuildFileInDir(buildFile, "org.junit.jupiter:junit-jupiter:${dependencyVersion}", "org.junit.jupiter.api")
    }

    static rewriteBuildFileWithVintage(File buildFile, String dependencyVersion) {
        rewriteBuildFileInDir(buildFile, "org.junit.vintage:junit-vintage-engine:${dependencyVersion}','junit:junit:4.13")
    }

    static rewriteJavaFilesWithJupiterAnno(File rootProject) {
        rootProject.traverse(type: FileType.FILES, nameFilter: ~/.*\.(java|groovy)/) {
            String text = it.text
            REPLACEMENTS.each { key, value ->
                text = text.replace(key, value)
            }
            TRANSFORMS.each { transform ->
                text = transform(text)
            }
            it.text = text
        }
    }

    static rewriteJavaModuleFileWithJupiterRequires(File rootProject) {
        def moduleInfo = new File(rootProject, 'src/test/java/module-info.java')
        if (moduleInfo.exists()) {
            moduleInfo.text = moduleInfo.text.replace('requires junit', 'requires org.junit.jupiter.api')
        }
    }

    static rewriteBuildFileInDir(File dir, String dependencies, String moduleName = null) {
        def dirs = [dir]
        dirs.addAll(dir.listFiles().findAll { it.isDirectory() })
        dirs.each {
            File buildFile = new File(it, 'build.gradle')
            if (buildFile.exists()) {
                rewriteBuildFile(buildFile, dependencies, moduleName)
            }
        }
    }

    static rewriteBuildFile(File buildFile, String dependenciesReplacement, String moduleName) {
        String text = buildFile.text
        // compile/testCompile/implementation/testImplementation
        text = text.replaceFirst(/ompile ['"]junit:junit:4\.13['"]/, "ompile '${dependenciesReplacement}'")
        text = text.replaceFirst(/mplementation ['"]junit:junit:4\.13['"]/, "mplementation '${dependenciesReplacement}'")
        if (!text.contains('useTestNG')) {
            // we only hack build with JUnit 4
            // See IncrementalTestIntegrationTest.executesTestsWhenTestFrameworkChanges
            if (text.contains("useJUnit {")) {
                text = text.replace('useJUnit {', 'useJUnitPlatform {')
            } else if (text.contains('useJUnit()')) {
                text = text.replace('useJUnit()', 'useJUnitPlatform()')
            } else if (text.contains('tasks.named("test") {')) {
                text = text.replace('tasks.named("test") {', '''
tasks.named("test") {
    useJUnitPlatform()
''')
            } else if (text.contains('test {')) {
                text = text.replace('test {', '''
tasks.named("test") {
    useJUnitPlatform()
''')
            } else {
                text += '''
tasks.named("test") {
    useJUnitPlatform()
}
                '''
            }
        }
        if (moduleName != null && text.contains("--add-modules")) {
            // This is a bit aggressive but it works for now
            text = text.replace('"junit"', '"' + moduleName + '"')
            text = text.replace('=junit"', '=' + moduleName + '"')
        }
        buildFile.text = text
    }
}
