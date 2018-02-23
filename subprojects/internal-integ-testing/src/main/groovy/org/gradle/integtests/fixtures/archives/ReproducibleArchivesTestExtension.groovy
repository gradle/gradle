/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.integtests.fixtures.archives

import groovy.transform.CompileStatic
import org.spockframework.runtime.extension.AbstractAnnotationDrivenExtension
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.SpecInfo

@CompileStatic
class ReproducibleArchivesTestExtension extends AbstractAnnotationDrivenExtension<TestReproducibleArchives> {
    @Override
    void visitSpecAnnotation(TestReproducibleArchives annotation, SpecInfo spec) {
        spec.features.each { feature ->
            runForReproducibleArchives(feature)
        }
    }

    @Override
    void visitFeatureAnnotation(TestReproducibleArchives annotation, FeatureInfo feature) {
        runForReproducibleArchives(feature)
    }

    @Override
    void visitSpec(SpecInfo spec) {
        spec.features.each { FeatureInfo feature ->
            feature.interceptors.find { it instanceof ReproducibleArchivesInterceptor }.each { ReproducibleArchivesInterceptor interceptor ->
                // Add the name provider as late as possible to capture name providers from other extensions (e.g. @Unroll)
                feature.iterationNameProvider = interceptor.nameProvider(feature.iterationNameProvider)
            }
        }
    }

    private static void runForReproducibleArchives(FeatureInfo feature) {
        def interceptor = new ReproducibleArchivesInterceptor()
        feature.reportIterations = true
        feature.addInterceptor(interceptor)
        feature.addIterationInterceptor(interceptor)
    }
}

