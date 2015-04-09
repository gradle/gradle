/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.language

import org.gradle.model.internal.core.ModelPath
import org.gradle.model.internal.type.ModelType
import org.gradle.nativeplatform.internal.pch.PreCompiledHeaderTransformContainer
import org.gradle.nativeplatform.plugins.NativeComponentModelPlugin
import org.gradle.util.TestUtil
import spock.lang.Specification

abstract class AbstractPCHPluginSpec extends Specification {
    final def project = TestUtil.createRootProject()

    abstract def getPluginClass()

    abstract def getLanguageSourceSet()

    def "registers sourceset in pch transform container"() {
        when:
        project.pluginManager.apply(NativeComponentModelPlugin)
        project.pluginManager.apply(pluginClass)
        project.evaluate()

        then:
        def pchTransforms = project.modelRegistry.realize(new ModelPath("preCompiledHeaderTransformContainer"), ModelType.of(PreCompiledHeaderTransformContainer))
        pchTransforms.find { it.sourceSetType == languageSourceSet } != null
    }
}
