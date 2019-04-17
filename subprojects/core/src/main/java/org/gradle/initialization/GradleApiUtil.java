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

package org.gradle.initialization;

import org.gradle.internal.classloader.FilteringClassLoader;
import org.gradle.internal.reflect.Instantiator;

public class GradleApiUtil {
    public static FilteringClassLoader.Spec apiSpecFor(ClassLoader classLoader, Instantiator instantiator) {
        FilteringClassLoader.Spec apiSpec = new FilteringClassLoader.Spec();
        GradleApiSpecProvider.Spec apiAggregate = new GradleApiSpecAggregator(classLoader, instantiator).aggregate();
        for (String resource : apiAggregate.getExportedResources()) {
            apiSpec.allowResource(resource);
        }
        for (String resourcePrefix : apiAggregate.getExportedResourcePrefixes()) {
            apiSpec.allowResources(resourcePrefix);
        }
        for (Class<?> clazz : apiAggregate.getExportedClasses()) {
            apiSpec.allowClass(clazz);
        }
        for (String packageName : apiAggregate.getExportedPackages()) {
            apiSpec.allowPackage(packageName);
        }
        return apiSpec;
    }
}
