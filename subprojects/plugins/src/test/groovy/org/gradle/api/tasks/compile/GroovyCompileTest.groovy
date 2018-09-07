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

package org.gradle.api.tasks.compile

import org.apache.commons.io.FileUtils
import org.gradle.api.InvalidUserDataException
import org.gradle.api.file.FileCollection
import org.gradle.api.internal.ConventionTask
import org.gradle.api.internal.tasks.compile.GroovyJavaJointCompileSpec
import org.gradle.api.tasks.WorkResult
import org.gradle.language.base.internal.compile.Compiler
import spock.lang.Unroll

public class GroovyCompileTest extends AbstractCompileTest {
    private static final boolean EMPTY_CLASSPATH = true
    private static final boolean NON_EMPTY_CLASSPATH = false
    private GroovyCompile testObj

    Compiler<GroovyJavaJointCompileSpec> groovyCompilerMock = Mock()

    @Override
    public AbstractCompile getCompile() {
        return testObj
    }

    @Override
    public ConventionTask getTask() {
        return testObj
    }

    def setup() {
        testObj = createTask(GroovyCompile.class)
        testObj.setCompiler(groovyCompilerMock)

        FileUtils.touch(new File(srcDir, "incl/file.groovy"))
    }

    @Unroll
    def "execute: doing work == #doingWork"() {
        given:
        setUpMocksAndAttributes(testObj, NON_EMPTY_CLASSPATH)

        when:
        testObj.compile()

        then:
        1 * groovyCompilerMock.execute(_ as GroovyJavaJointCompileSpec) >> new ExpectedWorkResult(doingWork)
        testObj.didWork == doingWork

        where:
        doingWork << [true, false]
    }

    def "moan if groovy classpath is empty"() {
        given:
        setUpMocksAndAttributes(testObj, EMPTY_CLASSPATH)

        when:
        testObj.compile()

        then:
        def e = thrown InvalidUserDataException
        e.message.contains("'testTask.groovyClasspath' must not be empty.")
    }

    private void setUpMocksAndAttributes(GroovyCompile compile, final boolean groovyClasspathEmpty) {
        super.setUpMocksAndAttributes(compile)

        final FileCollection groovyClasspathCollection = Stub(FileCollection, {
            isEmpty() >> groovyClasspathEmpty
        })
        compile.setGroovyClasspath(groovyClasspathCollection)
        compile.source(srcDir)
    }

    private class ExpectedWorkResult implements WorkResult {
        private boolean didWork

        ExpectedWorkResult(boolean didWork) {
            this.didWork = didWork
        }

        @Override
        boolean getDidWork() {
            return didWork
        }
    }
}
