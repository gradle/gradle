/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.api.internal.artifacts.dsl.dependencies;

import org.gradle.api.artifacts.CacheableRule;
import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;
import org.gradle.api.artifacts.DependencyConstraintMetadata;
import org.gradle.api.artifacts.DependencyMetadata;
import org.gradle.api.artifacts.DirectDependencyMetadata;

import java.util.List;

@CacheableRule
public class EnforcedPlatformComponentMetadataRule implements ComponentMetadataRule {

    @Override
    public void execute(ComponentMetadataContext context) {
        context.getDetails().allVariants(variantMetadata -> {
            variantMetadata.withDependencyConstraints(constraints -> {
                for (DependencyConstraintMetadata constraint : constraints) {
                    makeStrict(constraint);
                }
            });
            variantMetadata.withDependencies(dependencies -> {
                for (DirectDependencyMetadata dependency : dependencies) {
                    makeStrict(dependency);
                }
            });
        });
    }

    private void makeStrict(DependencyMetadata<?> dependency) {
        List<String> rejectedVersions = dependency.getVersionConstraint().getRejectedVersions();
        dependency.version(v -> {
            v.strictly(v.getRequiredVersion());
            if (!rejectedVersions.isEmpty()) {
                v.reject(rejectedVersions.toArray(new String[0]));
            }
        });
    }
}
