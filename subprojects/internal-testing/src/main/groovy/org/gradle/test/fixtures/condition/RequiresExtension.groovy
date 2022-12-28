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
package org.gradle.test.fixtures.condition

import groovy.transform.CompileStatic
import org.spockframework.runtime.extension.IAnnotationDrivenExtension
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.SpecInfo

@CompileStatic
class RequiresExtension implements IAnnotationDrivenExtension<Requires> {

    @Override
    void visitSpecAnnotation(Requires annotation, SpecInfo spec) {
        // If all preconditions are met, we DON'T skip the tests
        spec.skipped = !TestPrecondition.doSatisfiesAll(annotation.value())
    }

    @Override
    void visitFeatureAnnotation(Requires annotation, FeatureInfo feature) {
        // If all preconditions are met, we DON'T skip the tests
        feature.skipped = !TestPrecondition.doSatisfiesAll(annotation.value())
    }
}
