/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle;

import org.gradle.api.DependencyManager;
import org.gradle.api.Project;
import org.gradle.util.GFileUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class DefaultBuildListener implements BuildListener {
    private static Logger logger = LoggerFactory.getLogger(DefaultBuildListener.class);

    public void buildFinished(BuildResult result) {
        if (result.getSettings() == null) {
            return;
        }
        File rootDir = result.getSettings().getRootDir();
        try {
            // todo: For some reasons this has failed under windows. We need to check what the problem is. 
            GFileUtils.deleteDirectory(new File(rootDir, Project.TMP_DIR_NAME + '/' + DependencyManager.BUILD_RESOLVER_NAME));
        } catch (Exception e) {
            logger.debug("Can't delete the build-resolver dir. We continue.", e);
        }
    }
}
