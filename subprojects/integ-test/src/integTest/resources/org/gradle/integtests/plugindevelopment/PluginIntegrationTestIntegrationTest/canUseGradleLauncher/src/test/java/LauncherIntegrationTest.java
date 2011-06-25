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

import org.junit.Test;

import org.junit.Rule;
import org.junit.rules.TemporaryFolder;

import org.gradle.GradleLauncher;
import org.gradle.BuildResult;

import org.gradle.util.GFileUtils;

import java.io.File;
import java.net.URL;

public class LauncherIntegrationTest {

    @Rule public TemporaryFolder tmpDir = new TemporaryFolder();
    
    @Test
    public void useLauncher() throws Exception {
        String projectName = "basicjava";
        File projectDir = tmpDir.newFolder(projectName);
        GFileUtils.copyDirectory(sourceProject(projectName), projectDir);
        GradleLauncher launcher = GradleLauncher.newInstance("--project-dir", projectDir.getAbsolutePath(), "build");
        BuildResult result = launcher.run();
    }
    
    
    File sourceProject(String name) {
        return getResourceFile("projects/" + name);
    }
    
    File getResourceFile(String path) {
        URL resource = getClass().getClassLoader().getResource(path);
        assert resource != null : "resource file '" + path + "' does not exist";
        try {
            return new File(resource.toURI());
        } catch (Exception e) {
            throw new RuntimeException("could not get resource file: " + path, e);
        }
    }
    
}