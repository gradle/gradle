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

package org.gradle.groovy.scripts.internal

import org.gradle.api.internal.DocumentationRegistry
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.project.ProjectScript
import org.gradle.api.problems.Problems
import org.gradle.configuration.ImportsReader
import org.gradle.configuration.ScriptTarget
import org.gradle.configuration.project.DefaultCompileOperationFactory
import org.gradle.groovy.scripts.TextResourceScriptSource
import org.gradle.internal.Actions
import org.gradle.internal.file.Deleter
import org.gradle.internal.resource.StringTextResource
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

/**
 * Covers the initial "classpath" pass, which keeps only the {@code buildscript} and {@code plugins}
 * blocks and discards everything else in the script.
 */
class SubsetScriptTransformerSpec extends Specification {

    @Rule
    public TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def importsReader = Mock(ImportsReader) {
        getImportPackages() >> ([] as String[])
        getSimpleNameToFullClassNamesMapping() >> [:]
    }

    final DefaultScriptCompilationHandler scriptCompilationHandler = new DefaultScriptCompilationHandler(
        importsReader
    ) {
        @Override
        protected Deleter getDeleter() {
            return TestFiles.deleter()
        }

        @Override
        protected Problems getProblemsService() {
            return TestUtil.problemsService()
        }
    }

    File scriptCacheDir
    File metadataCacheDir

    def setup() {
        File testProjectDir = tmpDir.createDir("projectDir")
        scriptCacheDir = new File(testProjectDir, "cache")
        metadataCacheDir = new File(testProjectDir, "metadata")
    }

    private void compileInitialPass(String script) {
        def source = new TextResourceScriptSource(new StringTextResource("test script", script))
        def target = Mock(ScriptTarget) {
            getId() >> "test"
            getClasspathBlockName() >> "buildscript"
            getPluginsBlockName() >> "plugins"
        }
        def operation = new DefaultCompileOperationFactory(new DocumentationRegistry()).getPluginsBlockCompileOperation(target)
        scriptCompilationHandler.compileToDir(source, getClass().classLoader, scriptCacheDir, metadataCacheDir, operation, ProjectScript, Actions.doNothing())
    }

    /**
     * The types below are deliberately absent from the classpath of this pass, which is what a script
     * referring to a type from a plugin it declares looks like. The declaring statement is discarded,
     * but the anonymous class it created stays attached to the script class, so the pass only succeeds
     * if that class no longer refers to the missing type.
     */
    def "compiles when an anonymous class #description"() {
        when:
        compileInitialPass("""
            buildscript {
            }

            $declaration
        """)

        then:
        noExceptionThrown()

        where:
        description                        | declaration
        "extends a type off the classpath" | "def thing = new NotOnThisClasspath() { }"
        "is parameterized by one"          | "def thing = new NotOnThisClasspath<AlsoMissing>() { }"
        "declares a member using one"      | "def thing = new NotOnThisClasspath() { AlsoMissing member() { null } }"
        "nests another one"                | "def thing = new NotOnThisClasspath() { def nested = new AlsoMissing() { } }"
    }
}
