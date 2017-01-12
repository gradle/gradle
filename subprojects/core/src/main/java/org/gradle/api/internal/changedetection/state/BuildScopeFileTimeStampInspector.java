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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.invocation.Gradle;
import org.gradle.cache.internal.CacheScopeMapping;
import org.gradle.cache.internal.VersionStrategy;
import org.gradle.internal.concurrent.Stoppable;

public class BuildScopeFileTimeStampInspector extends FileTimeStampInspector implements Stoppable {

    public BuildScopeFileTimeStampInspector(Gradle gradle, CacheScopeMapping cacheScopeMapping) {
        super(cacheScopeMapping.getBaseDirectory(gradle, "file-changes", VersionStrategy.CachePerVersion));
        updateOnStartBuild();
    }

    @Override
    public String toString() {
        return "build scope cache";
    }

    @Override
    public void stop() {
        updateOnFinishBuild();
    }
}
