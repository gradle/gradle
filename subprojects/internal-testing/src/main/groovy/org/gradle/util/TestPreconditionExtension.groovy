/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.util

import org.spockframework.runtime.extension.AbstractAnnotationDrivenExtension
import org.spockframework.runtime.model.SpecInfo
import org.spockframework.runtime.model.FeatureInfo

class TestPreconditionExtension extends AbstractAnnotationDrivenExtension<Requires> {
    @Override
    void visitSpecAnnotation(Requires annotation, SpecInfo spec) {
        spec.skipped |= unsatisfied(annotation)
    }

    @Override
    void visitFeatureAnnotation(Requires annotation, FeatureInfo feature) {
        feature.skipped |= unsatisfied(annotation)
    }

    private boolean unsatisfied(Requires annotation) {
        annotation.value().any { !it.fulfilled } || !annotation.adhoc().newInstance(null, null).call()
    }
}
