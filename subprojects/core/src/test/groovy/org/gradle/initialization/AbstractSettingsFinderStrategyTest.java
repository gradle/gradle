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
package org.gradle.initialization;

import org.gradle.StartParameter;
import org.gradle.api.initialization.Settings;
import org.gradle.util.TemporaryFolder;
import org.junit.Rule;
import org.junit.Test;

import java.io.File;
import java.io.IOException;

/**
 * @author Hans Dockter
 */
public abstract class AbstractSettingsFinderStrategyTest {
    @Rule
    public TemporaryFolder tmpDir = new TemporaryFolder();
    protected File testDir = tmpDir.getDir();
    protected File currentDir;

    protected abstract ISettingsFileSearchStrategy getStrategy();

    protected File createSettingsFile(File dir) {
        File file = new File(dir, Settings.DEFAULT_SETTINGS_FILE);
        try {
            file.createNewFile();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        file.deleteOnExit();
        return file;
    }

    protected StartParameter createStartParams(boolean searchUpwards) {
        StartParameter startParameter = new StartParameter();
        startParameter.setCurrentDir(currentDir);
        startParameter.setSearchUpwards(searchUpwards);
        return startParameter;
    }

    @Test
    public void findExistingSettingsInCurrentDirWithSearchUpwardsTrue() {
        org.junit.Assert.assertEquals(createSettingsFile(currentDir), getStrategy().find(createStartParams(true)));
    }

    @Test
    public void findExistingSettingsInCurrentDirWithSearchUpwardsFalse() {
        org.junit.Assert.assertEquals(createSettingsFile(currentDir), getStrategy().find(createStartParams(false)));
    }

    @Test
    public void findNonExistingSettingsInCurrentDirWithSearchUpwardsFalse() {
        org.junit.Assert.assertEquals(null, getStrategy().find(createStartParams(false)));
    }
}
