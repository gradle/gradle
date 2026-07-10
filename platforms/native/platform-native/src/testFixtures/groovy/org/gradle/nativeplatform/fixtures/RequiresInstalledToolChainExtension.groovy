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
package org.gradle.nativeplatform.fixtures


import org.spockframework.runtime.extension.IAnnotationDrivenExtension
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.SpecInfo

class RequiresInstalledToolChainExtension implements IAnnotationDrivenExtension<RequiresInstalledToolChain> {
    @Override
    void visitSpecAnnotation(RequiresInstalledToolChain annotation, SpecInfo spec) {
        final available = isToolChainAvailable(annotation)
        spec.skipped |= !available
    }

    @Override
    void visitFeatureAnnotation(RequiresInstalledToolChain annotation, FeatureInfo feature) {
        final available = isToolChainAvailable(annotation)
        feature.skipped |= !available
    }

    private static boolean isToolChainAvailable(RequiresInstalledToolChain annotation) {
        def requiredToolChain = AvailableToolChains.getToolChain(annotation.value())
        return requiredToolChain != null
    }
}
