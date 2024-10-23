/*
 * Copyright 2024 the original author or authors.
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

import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import org.spockframework.runtime.extension.IAnnotationDrivenExtension
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.SpecElementInfo
import org.spockframework.runtime.model.SpecInfo

class ToBeFixedForIsolatedProjectsExtension implements IAnnotationDrivenExtension<ToBeFixedForIsolatedProjects> {

    private final ToBeFixedSpecInterceptor toBeFixedSpecInterceptor = new ToBeFixedSpecInterceptor("Isolated Projects")

    @Override
    void visitSpecAnnotation(ToBeFixedForIsolatedProjects annotation, SpecInfo spec) {
        visitAnnotation(spec)
    }

    @Override
    void visitFeatureAnnotation(ToBeFixedForIsolatedProjects annotation, FeatureInfo feature) {
        visitAnnotation(feature)
    }

    private void visitAnnotation(SpecElementInfo specElementInfo) {
        if (GradleContextualExecuter.isNotIsolatedProjects()) {
            return
        }

        toBeFixedSpecInterceptor.intercept(specElementInfo, new String[0])
    }
}
