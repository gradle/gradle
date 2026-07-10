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

package org.gradle.integtests.fixtures

import groovy.io.FileType
import org.codehaus.groovy.control.CompilerConfiguration
import org.gradle.internal.FileUtils
import org.gradle.util.internal.TextUtil

import java.util.function.Predicate

import static org.apache.commons.io.FilenameUtils.removeExtension
import static org.spockframework.util.CollectionUtil.asSet

class CompilationOutputsFixture {

    private final File targetDir
    private final List<String> includeExtensions
    private final String sourceSet
    private final String lang

    CompilationOutputsFixture(File targetDir) {
        this(targetDir, [])
    }
    /**
     * Tracks outputs in given target dir considering only the files by the given extensions (ignoring case)
     */
    CompilationOutputsFixture(File targetDir, List<String> includeExtensions, String sourceSet = "main", String lang = "java") {
        assert targetDir != null
        this.targetDir = targetDir
        this.sourceSet = sourceSet
        this.lang = lang
        this.includeExtensions = includeExtensions
    }

    private List<File> snapshot = []

    // Executes optional operation and makes a snapshot of outputs (sets the last modified timestamp to zero for all files)
    public <T> T snapshot(Closure<T> operation = null) {
        T result = operation?.call()
        snapshot.clear()
        targetDir.eachFileRecurse(FileType.FILES) {
            if (isIncluded(it)) {
                it.lastModified = 0
                snapshot << it
            }
        }
        result
    }

    private boolean isIncluded(File file) {
        includeExtensions.empty || includeExtensions.any {
            FileUtils.hasExtensionIgnoresCase(file.name, it)
        }
    }

    //asserts none of the files changed/added since last snapshot
    void noneRecompiled() {
        recompiledFiles([])
    }

    //asserts file changed/added since last snapshot
    void recompiledFile(File file) {
        recompiledFiles([file])
    }

    //asserts files changed/added since last snapshot
    void recompiledFiles(File... files) {
        recompiledFiles(files as List)
    }

    //asserts files changed/added since last snapshot
    void recompiledFiles(Collection<File> files) {
        def expectedNames = files.collect({ removeExtension(it.name) }) as Set
        assert changedFileNames == expectedNames
    }

    //asserts has the exact set of output files
    void hasFiles(File... files) {
        def expectedNames = files.collect({ removeExtension(it.name) }) as Set
        assert getFiles { true } == expectedNames
    }

    //asserts file changed/added since last snapshot
    void recompiledFile(String fileName) {
        recompiledFiles(fileName)
    }

    //asserts files changed/added since last snapshot
    void recompiledFiles(String... fileNames) {
        def expectedNames = fileNames.collect({ removeExtension(it) }) as Set
        assert changedFileNames == expectedNames
    }

    //asserts classes changed/added since last snapshot. Class means file name without extension.
    void recompiledClasses(String... classNames) {
        assert changedFileNames == asSet(classNames)
    }

    void recompiledFqn(String... classNames) {
        assert getChangedFileNames(true) == asSet(classNames)
    }

    //asserts files deleted since last snapshot.
    void deletedFiles(String... fileNames) {
        def expectedNames = fileNames.collect({ removeExtension(it) }) as Set
        def deleted = snapshot.findAll { !it.exists() }.collect { removeExtension(it.name) } as Set
        assert deleted == expectedNames
    }

    //asserts classes deleted since last snapshot. Class means file name without extension.
    void deletedClasses(String... classNames) {
        def deleted = snapshot.findAll { !it.exists() }.collect { removeExtension(it.name) } as Set
        assert deleted == asSet(classNames)
    }

    private Set<String> getChangedFileNames(boolean qualified = false) {
        // Get all of the files that do not have a zero last modified timestamp
        return getFiles(qualified) { it.lastModified() > 0 }
    }

    private Set<String> getFiles(boolean qualified = false, Predicate<File> criteria) {
        // Get all of the files that do not have a zero last modified timestamp
        def changed = new HashSet()
        def dir = qualified ? new File(new File(targetDir, lang), sourceSet) : targetDir
        dir.eachFileRecurse(FileType.FILES) {
            if (isIncluded(it)) {
                if (criteria.test(it)) {
                    if (qualified) {
                        String relative = dir.toPath().relativize(it.toPath()).toString()
                        changed << TextUtil.normaliseFileSeparators(removeExtension(relative)).replace('/', '.')
                    } else {
                        changed << removeExtension(it.name)
                    }
                }
            }
        }
        changed
    }

    Object evaluate(@GroovyBuildScriptLanguage String script) {
        CompilerConfiguration config = new CompilerConfiguration()
        config.classpath.add("${targetDir}/$lang/$sourceSet".toString())
        new GroovyShell(config).evaluate(script)
    }

    void execute(@GroovyBuildScriptLanguage String script) {
        evaluate(script)
    }
}
