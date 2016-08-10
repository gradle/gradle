/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.composite;

import com.google.common.collect.Sets;
import org.gradle.StartParameter;
import org.gradle.api.Transformer;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.initialization.SettingsLoader;
import org.gradle.internal.service.ServiceRegistry;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.util.Collection;
import java.util.Set;

public class CompositeBuildSettingsLoader implements SettingsLoader {
    private final SettingsLoader delegate;
    private final ServiceRegistry buildServices;

    public CompositeBuildSettingsLoader(SettingsLoader delegate, ServiceRegistry buildServices) {
        this.delegate = delegate;
        this.buildServices = buildServices;
    }

    @Override
    public SettingsInternal findAndLoadSettings(GradleInternal gradle) {
        SettingsInternal settings = delegate.findAndLoadSettings(gradle);

        Collection<IncludedBuild> includedBuilds = getIncludedBuilds(gradle.getStartParameter(), settings);
        if (!includedBuilds.isEmpty()) {
            CompositeContextBuilder compositeContextBuilder = buildServices.get(CompositeContextBuilder.class);
            compositeContextBuilder.addToCompositeContext(includedBuilds);
        }

        return settings;
    }

    private Collection<IncludedBuild> getIncludedBuilds(StartParameter startParameter, SettingsInternal settings) {
        Set<File> buildRoots = Sets.newTreeSet();
        buildRoots.addAll(startParameter.getIncludedBuilds());
        buildRoots.addAll(settings.getIncludedBuilds());

        return CollectionUtils.collect(buildRoots, new Transformer<IncludedBuild, File>() {
            @Override
            public IncludedBuild transform(File buildRoot) {
                return new DefaultIncludedBuild(buildRoot);
            }
        });
    }

}
