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

package org.gradle.buildinit.plugins.internal;

import org.gradle.buildinit.plugins.internal.modifiers.BuildInitDsl;

import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

public abstract class AbstractBuildGenerator implements BuildGenerator {
    private final BuildContentGenerator generator;
    private final List<? extends BuildContentGenerator> generators;

    public AbstractBuildGenerator(BuildContentGenerator projectGenerator, List<? extends BuildContentGenerator> generators) {
        this.generators = generators;
        this.generator = projectGenerator;
    }

    @Override
    public boolean supportsProjectName() {
        return true;
    }

    @Override
    public Set<BuildInitDsl> getDsls() {
        return new TreeSet<>(Arrays.asList(BuildInitDsl.values()));
    }

    @Override
    public void generate(InitSettings settings) {
        BuildContentGenerationContext buildContentGenerationContext = new BuildContentGenerationContext(new VersionCatalogDependencyRegistry(false));
        for (BuildContentGenerator generator : generators) {
            generator.generate(settings, buildContentGenerationContext);
        }
        generator.generate(settings, buildContentGenerationContext);
        VersionCatalogGenerator.create(settings.getTarget()).generate(buildContentGenerationContext, settings.isWithComments());
    }
}
