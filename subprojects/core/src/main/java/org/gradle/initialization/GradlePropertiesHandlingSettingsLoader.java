/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.initialization;

import org.gradle.api.internal.DynamicObjectAware;
import org.gradle.api.internal.GradleInternal;
import org.gradle.api.internal.SettingsInternal;
import org.gradle.initialization.layout.BuildLayoutConfiguration;
import org.gradle.initialization.layout.BuildLayoutFactory;
import org.gradle.internal.extensibility.ExtensibleDynamicObject;
import org.gradle.internal.metaobject.DynamicObject;

import java.util.Map;

import static java.util.Collections.emptyMap;

public class GradlePropertiesHandlingSettingsLoader implements SettingsLoader {
    private final SettingsLoader delegate;
    private final BuildLayoutFactory buildLayoutFactory;
    private final GradlePropertiesController gradlePropertiesController;

    public GradlePropertiesHandlingSettingsLoader(SettingsLoader delegate, BuildLayoutFactory buildLayoutFactory, GradlePropertiesController gradlePropertiesController) {
        this.delegate = delegate;
        this.buildLayoutFactory = buildLayoutFactory;
        this.gradlePropertiesController = gradlePropertiesController;
    }

    @Override
    public SettingsInternal findAndLoadSettings(GradleInternal gradle) {
        SettingsLocation settingsLocation = buildLayoutFactory.getLayoutFor(new BuildLayoutConfiguration(gradle.getStartParameter()));
        gradlePropertiesController.loadGradlePropertiesFrom(settingsLocation.getSettingsDir());

        // Make the GradleProperties values available for use inside of init scripts
        Map<String, String> gradleProperties = gradlePropertiesController.getGradleProperties().mergeProperties(emptyMap());
        DynamicObject dynamicObject = ((DynamicObjectAware) gradle).getAsDynamicObject();
        ((ExtensibleDynamicObject) dynamicObject).addProperties(gradleProperties);

        return delegate.findAndLoadSettings(gradle);
    }
}
