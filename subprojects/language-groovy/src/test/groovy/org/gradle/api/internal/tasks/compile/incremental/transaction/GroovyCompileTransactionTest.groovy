/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.transaction

import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.tasks.compile.DefaultGroovyJavaJointCompileSpec
import org.gradle.api.internal.tasks.compile.GroovyJavaJointCompileSpec
import org.gradle.api.internal.tasks.compile.JavaCompileSpec
import org.gradle.api.internal.tasks.compile.MinimalGroovyCompileOptions
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.api.tasks.compile.GroovyCompileOptions
import org.gradle.api.tasks.util.PatternSet
import org.gradle.util.TestUtil

class GroovyCompileTransactionTest extends AbstractCompileTransactionTest {

    @Override
    JavaCompileSpec newCompileSpec() {
        JavaCompileSpec spec = new DefaultGroovyJavaJointCompileSpec()
        spec.setCompileOptions(TestUtil.newInstance(CompileOptions, TestUtil.objectFactory()))
        spec.setGroovyCompileOptions(new MinimalGroovyCompileOptions(TestUtil.newInstance(GroovyCompileOptions)))
        spec.setSourceFiles([])
        return spec
    }

    @Override
    CompileTransaction newCompileTransaction() {
        return new GroovyCompileTransaction(spec, new PatternSet(), Collections.emptyMap(), TestFiles.fileOperations(temporaryFolder), TestFiles.deleter())
    }

    @Override
    CompileTransaction newCompileTransaction(PatternSet classesToDelete) {
        return new GroovyCompileTransaction(spec, classesToDelete, Collections.emptyMap(), TestFiles.fileOperations(temporaryFolder), TestFiles.deleter())
    }

    @Override
    CompileTransaction newCompileTransaction(PatternSet classesToDelete, Map<GeneratedResource.Location, PatternSet> resourcesToDelete) {
        return new GroovyCompileTransaction(spec, classesToDelete, resourcesToDelete, TestFiles.fileOperations(temporaryFolder), TestFiles.deleter())
    }

    def "does not stash files automatically before execution when Java sources are included in the compilation"() {
        def destinationDir = spec.getDestinationDir()
        def javaSource = new File(destinationDir, "JavaClass.java")
        javaSource.createNewFile()
        spec.setSourceFiles([javaSource])
        def filesToDelete = new PatternSet().include("**/*.java")

        when:
        newCompileTransaction(filesToDelete).execute {
            // File was not stashed before
            assert stashDir.list() as Set ==~ []
            assert !isEmptyDirectory(destinationDir)

            // We can stashed file during action
            (spec as GroovyJavaJointCompileSpec).beforeJavaCompilationRunnable.run()
            assert stashDir.list() as Set ==~ ["JavaClass.java.uniqueId0"]
            return DID_WORK
        }

        then:
        isEmptyDirectory(destinationDir)
    }

    def "stashes files automatically before execution when Java sources are included but only groovy files are compiled"() {
        def destinationDir = spec.getDestinationDir()
        def javaSource = new File(destinationDir, "JavaClass.java")
        javaSource.createNewFile()
        spec.setSourceFiles([javaSource])
        (spec as GroovyJavaJointCompileSpec).getGroovyCompileOptions().setFileExtensions(["groovy"])
        def filesToDelete = new PatternSet().include("**/*.java")

        when:
        newCompileTransaction(filesToDelete).execute {
            // File was stashed before
            assert stashDir.list() as Set ==~ ["JavaClass.java.uniqueId0"]
            assert isEmptyDirectory(destinationDir)
            assert (spec as GroovyJavaJointCompileSpec).beforeJavaCompilationRunnable == null
            return DID_WORK
        }

        then:
        isEmptyDirectory(destinationDir)
    }
}
