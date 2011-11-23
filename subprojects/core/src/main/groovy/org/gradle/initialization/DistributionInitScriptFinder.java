/*
 * Copyright 2011 the original author or authors.
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

import org.gradle.api.internal.GradleDistributionLocator;
import org.gradle.api.internal.GradleInternal;
import org.gradle.groovy.scripts.ScriptSource;

import java.io.File;
import java.util.Collection;

/**
 * An {@link InitScriptFinder} that includes every *.gradle file in $gradleHome/init.d.
 */
public class DistributionInitScriptFinder extends DirectoryInitScriptFinder {
    final GradleDistributionLocator locator;

    public DistributionInitScriptFinder(GradleDistributionLocator locator) {
        this.locator = locator;
    }

    public void findScripts(GradleInternal gradle, Collection<ScriptSource> scripts) {
        File distDir = locator.getGradleHome();
        if (distDir == null) {
            return;
        }
        findScriptsInDir(new File(distDir, "init.d"), scripts);
    }

}
