/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.nativeplatform.internal.configure;

import org.gradle.api.Action;
import org.gradle.language.nativeplatform.HeaderExportingSourceSet;
import org.gradle.model.Defaults;
import org.gradle.model.RuleSource;
import org.gradle.nativeplatform.NativeComponentSpec;

/**
 * Cross cutting rules for all instances of {@link org.gradle.nativeplatform.NativeComponentSpec}
 */
@SuppressWarnings("UnusedDeclaration")
public class NativeComponentRules extends RuleSource {
    @Defaults
    public void applyHeaderSourceSetConventions(final NativeComponentSpec component) {
        component.getSource().withType(HeaderExportingSourceSet.class).afterEach(new Action<HeaderExportingSourceSet>() {
            @Override
            public void execute(HeaderExportingSourceSet headerSourceSet) {
                // Only apply default locations when none explicitly configured
                if (headerSourceSet.getExportedHeaders().getSrcDirs().isEmpty()) {
                    headerSourceSet.getExportedHeaders().srcDir(String.format("src/%s/headers", component.getName()));
                }

                headerSourceSet.getImplicitHeaders().setSrcDirs(headerSourceSet.getSource().getSrcDirs());
                headerSourceSet.getImplicitHeaders().include("**/*.h");
            }
        });
    }
}
