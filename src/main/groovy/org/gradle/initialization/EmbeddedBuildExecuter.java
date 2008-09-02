/*
 * Copyright 2007 the original author or authors.
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

import org.gradle.Build;
import org.gradle.StartParameter;
import org.gradle.Build.BuildFactory;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class EmbeddedBuildExecuter {
    BuildFactory buildFactory;

    public EmbeddedBuildExecuter() {}

    public EmbeddedBuildExecuter(BuildFactory buildFactory) {
        this.buildFactory = buildFactory;
    }

    public void execute(File buildResolverDir, StartParameter startParameter) {
        Build build = buildFactory.newInstance(null, buildResolverDir);
        build.run(startParameter);
    }

    public void executeEmbeddedScript(File buildResolverDir, String embeddedScript, StartParameter startParameter) {
        Build build = buildFactory.newInstance(embeddedScript,  buildResolverDir);
        build.runNonRecursivelyWithCurrentDirAsRoot(startParameter);
    }

    public BuildFactory getBuildFactory() {
        return buildFactory;
    }

    public void setBuildFactory(BuildFactory buildFactory) {
        this.buildFactory = buildFactory;
    }
}
