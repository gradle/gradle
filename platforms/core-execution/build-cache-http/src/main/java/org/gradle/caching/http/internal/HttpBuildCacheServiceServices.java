/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.caching.http.internal;

import org.gradle.caching.configuration.internal.BuildCacheServiceRegistration;
import org.gradle.caching.configuration.internal.DefaultBuildCacheServiceRegistration;
import org.gradle.caching.http.HttpBuildCache;
import org.gradle.internal.service.ServiceRegistration;
import org.gradle.internal.service.scopes.AbstractPluginServiceRegistry;
import org.gradle.util.GradleVersion;

public class HttpBuildCacheServiceServices extends AbstractPluginServiceRegistry {

    @Override
    public void registerBuildServices(ServiceRegistration registration) {
        registration.add(BuildCacheServiceRegistration.class, new DefaultBuildCacheServiceRegistration(HttpBuildCache.class, DefaultHttpBuildCacheServiceFactory.class));
        registration.add(HttpBuildCacheRequestCustomizer.class, request -> request.addHeader("X-Gradle-Version", GradleVersion.current().getVersion()));
    }
}
