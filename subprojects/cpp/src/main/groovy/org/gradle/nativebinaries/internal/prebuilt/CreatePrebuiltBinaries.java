/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.nativebinaries.internal.prebuilt;

import org.gradle.internal.reflect.Instantiator;
import org.gradle.model.ModelRule;
import org.gradle.nativebinaries.*;
import org.gradle.nativebinaries.platform.PlatformContainer;

public class CreatePrebuiltBinaries extends ModelRule {
    private final Instantiator instantiator;

    public CreatePrebuiltBinaries(Instantiator instantiator) {
        this.instantiator = instantiator;
    }

    public void create(Repositories repositories, PlatformContainer platforms, BuildTypeContainer buildTypes, FlavorContainer flavors) {
        PrebuiltBinaryFactory factory = new PrebuiltBinaryFactory(instantiator, platforms, buildTypes, flavors);
        for (PrebuiltLibraries prebuiltLibraries : repositories.withType(PrebuiltLibraries.class)) {
            for (PrebuiltLibrary prebuiltLibrary : prebuiltLibraries) {
                factory.initialise(prebuiltLibrary);
            }
        }
    }

}
