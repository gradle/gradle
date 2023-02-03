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

import org.gradle.initialization.RootBuildLifecycleListener;
import org.gradle.internal.service.scopes.Scopes;
import org.gradle.internal.service.scopes.ServiceScope;

import java.io.File;

/**
 * Used for the project cache file hash cache.
 *
 * There is no additional logic apart from what is also in {@link FileTimeStampInspector}.
 */
@ServiceScope(Scopes.BuildSession.class)
public class BuildSessionScopeFileTimeStampInspector extends FileTimeStampInspector implements RootBuildLifecycleListener {
    public BuildSessionScopeFileTimeStampInspector(File workDir) {
        super(workDir);
    }

    @Override
    public String toString() {
        return "build scope cache";
    }

    @Override
    public void afterStart() {
        updateOnStartBuild();
    }

    @Override
    public void beforeComplete() {
        updateOnFinishBuild();
    }
}
