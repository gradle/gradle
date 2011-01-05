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

import static org.junit.Assert.assertEquals;
import org.junit.Before;
import org.junit.Test;

import java.io.File;

/**
 * @author Hans Dockter
 */
public class ParentDirSettingsFinderStrategyTest extends AbstractSettingsFinderStrategyTest {
    private ParentDirSettingsFinderStrategy parentDirSettingsFinderStrategy;
    private File rootDir;

    protected ISettingsFileSearchStrategy getStrategy() {
        return parentDirSettingsFinderStrategy;
    }

    @Before
    public void setUp() {
        parentDirSettingsFinderStrategy = new ParentDirSettingsFinderStrategy();
        rootDir = new File(testDir, "root");
        rootDir.mkdirs();
        currentDir = new File(rootDir, "current");
        currentDir.mkdir();
    }

    @Test
    public void findExistingSettingsInMasterDirWithSearchUpwardsFalse() {
        createSettingsFile(rootDir);
        assertEquals(null, parentDirSettingsFinderStrategy.find(createStartParams(false)));
    }

    @Test
    public void findExistingSettingsInMasterDirWithSearchUpwardsTrue() {
        assertEquals(createSettingsFile(rootDir), parentDirSettingsFinderStrategy.find(createStartParams(true)));
    }
}