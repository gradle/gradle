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
import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec
import org.gradle.api.internal.tasks.compile.JavaCompileSpec
import org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.GeneratedResource
import org.gradle.api.tasks.compile.CompileOptions
import org.gradle.api.tasks.util.PatternSet
import org.gradle.util.TestUtil

class JavaCompileTransactionTest extends AbstractCompileTransactionTest {

    @Override
    JavaCompileSpec newCompileSpec() {
        JavaCompileSpec spec = new DefaultJavaCompileSpec()
        spec.setCompileOptions(TestUtil.newInstance(CompileOptions, TestUtil.objectFactory()))
        return spec
    }

    @Override
    CompileTransaction newCompileTransaction() {
        return new JavaCompileTransaction(spec, new PatternSet(), Collections.emptyMap(), TestFiles.fileOperations(temporaryFolder), TestFiles.deleter())
    }

    @Override
    CompileTransaction newCompileTransaction(PatternSet classesToDelete) {
        return new JavaCompileTransaction(spec, classesToDelete, Collections.emptyMap(), TestFiles.fileOperations(temporaryFolder), TestFiles.deleter())
    }

    @Override
    CompileTransaction newCompileTransaction(PatternSet classesToDelete, Map<GeneratedResource.Location, PatternSet> resourcesToDelete) {
        return new JavaCompileTransaction(spec, classesToDelete, resourcesToDelete, TestFiles.fileOperations(temporaryFolder), TestFiles.deleter())
    }
}
