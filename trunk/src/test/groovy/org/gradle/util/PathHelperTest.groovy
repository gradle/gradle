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
 
package org.gradle.util

import org.gradle.api.Project

/**
 * @author Hans Dockter
 */
class PathHelperTest extends GroovyTestCase {
    File rootDir

    void setUp() {
        rootDir ='/root' as File
    }
    void testWithCurrentDirIsRootDir() {
        File currentDir = '/root' as File
        assertEquals(Project.PATH_SEPARATOR, PathHelper.getCurrentProjectPath(rootDir, currentDir))
    }
    void testWithCurrentDirIsChildOfRootDir() {
        File currentDir = '/root/child' as File
        assertEquals(Project.PATH_SEPARATOR + "child", PathHelper.getCurrentProjectPath(rootDir, currentDir))
    }
    void testWithCurrentDirIsGrandChildOfRootDir() {
       File currentDir = '/root/child/grandchild' as File
        assertEquals(Project.PATH_SEPARATOR +  'child' + Project.PATH_SEPARATOR + 'grandchild', PathHelper.getCurrentProjectPath(rootDir, currentDir))
    }
}
