/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.kotlin.dsl.tooling.builders.r60

import org.gradle.integtests.fixtures.build.BuildSpec
import org.gradle.kotlin.dsl.tooling.builders.AbstractKotlinScriptModelCrossVersionTest
import org.gradle.test.fixtures.file.TestFile
import org.gradle.tooling.model.kotlin.dsl.KotlinDslScriptsModel

class AbstractKotlinDslScriptsModelCrossVersionSpec extends AbstractKotlinScriptModelCrossVersionTest {

    protected void assertModelMatchesBuildSpec(KotlinDslScriptsModel model, BuildSpec spec) {

        model.scriptModels.values().each { script ->
            assertContainsGradleKotlinDslJars(script.classPath)
        }

        def settingsClassPath = canonicalClasspathOf(model, spec.scripts.settings)
        assertNotContainsBuildSrc(settingsClassPath)
        assertContainsGradleKotlinDslJars(settingsClassPath)
        assertIncludes(settingsClassPath, spec.jars.settings)
        assertExcludes(settingsClassPath, spec.jars.root, spec.jars.a, spec.jars.b)

        def rootClassPath = canonicalClasspathOf(model, spec.scripts.root)
        assertContainsBuildSrc(rootClassPath)
        assertIncludes(rootClassPath, spec.jars.root)
        assertExcludes(rootClassPath, spec.jars.a, spec.jars.b)

        def aClassPath = canonicalClasspathOf(model, spec.scripts.a)
        assertContainsBuildSrc(aClassPath)
        assertIncludes(aClassPath, spec.jars.root, spec.jars.a)
        assertExcludes(aClassPath, spec.jars.b)

        def bClassPath = canonicalClasspathOf(model, spec.scripts.b)
        assertContainsBuildSrc(bClassPath)
        assertIncludes(bClassPath, spec.jars.root, spec.jars.b)
        assertExcludes(bClassPath, spec.jars.a)

        def precompiledClassPath = canonicalClasspathOf(model, spec.scripts.precompiled)
        assertNotContainsBuildSrc(precompiledClassPath)
        assertIncludes(precompiledClassPath, spec.jars.precompiled)
        assertExcludes(precompiledClassPath, spec.jars.root, spec.jars.a, spec.jars.b)
    }

    protected static void assertModelAppliedScripts(KotlinDslScriptsModel model, BuildSpec spec) {
        spec.appliedScripts.each { appliedScript ->
            def classPath = model.scriptModels[appliedScript.value].classPath
            assertContainsGradleKotlinDslJars(classPath)
            assertContainsBuildSrc(classPath)
            def appliedJar = spec.jars[appliedScript.key]
            assertIncludes(classPath, appliedJar)
            assertExcludes(classPath, *(spec.jars.values() - appliedJar) as TestFile[])
        }
    }
}
