/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.tasks.scala

import org.apache.commons.io.FileUtils
import org.gradle.api.internal.ConventionTask
import org.gradle.api.tasks.AbstractConventionTaskTest
import org.gradle.api.tasks.compile.AbstractCompile
import org.gradle.language.base.internal.compile.Compiler
import org.gradle.util.internal.WrapUtil

class ScalaCompileTest extends AbstractConventionTaskTest {
    public static final String TEST_PATTERN_1 = "pattern1"
    public static final String TEST_PATTERN_2 = "pattern2"
    public static final String TEST_PATTERN_3 = "pattern3"

    protected File srcDir
    protected File destDir
    protected File depCacheDir

    private ScalaCompile scalaCompile
    private scalaCompiler = Mock(Compiler)

    AbstractCompile getCompile() {
        return scalaCompile
    }

    @Override
    ConventionTask getTask() {
        return scalaCompile
    }

    def setup() {
        destDir = project.file("destDir")
        depCacheDir = project.file("depCache")
        srcDir = project.file("src")
        srcDir.mkdirs()

        scalaCompile = createTask(ScalaCompile)
        scalaCompile.setCompiler(scalaCompiler)

        FileUtils.touch(new File(srcDir, "incl/file.scala"))
        FileUtils.touch(new File(srcDir, "incl/file.java"))
    }

    def "default values"() {
        given:
        def compile = getCompile()

        expect:
        !compile.getDestinationDirectory().isPresent()
        compile.getSourceCompatibility() == null
        compile.getTargetCompatibility() == null
        compile.getSource().isEmpty()
    }

    def "test includes"() {
        given:
        AbstractCompile compile = getCompile()

        expect:
        compile.is(compile.include(TEST_PATTERN_1, TEST_PATTERN_2))
        compile.getIncludes() == WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2)

        and:
        compile.is(compile.include(TEST_PATTERN_3))
        compile.getIncludes() == WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3)
    }

    def "test excludes"() {
        given:
        AbstractCompile compile = getCompile()

        expect:
        compile.is(compile.exclude(TEST_PATTERN_1, TEST_PATTERN_2))
        compile.getExcludes() == WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2)

        and:
        compile.is(compile.exclude(TEST_PATTERN_3))
        compile.getExcludes() == WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3)
    }
}
