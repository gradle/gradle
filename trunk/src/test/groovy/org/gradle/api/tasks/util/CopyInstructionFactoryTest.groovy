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
 
package org.gradle.api.tasks.util

/**
 * @author Hans Dockter
 */
class CopyInstructionFactoryTest extends GroovyTestCase {      
    void testCreateCopyInstruction() {
        File expectedSourceDir = 'src' as File
        File expectedTargetDir = 'target' as File
        Set expectedIncludes = ['include'] as Set
        Set expectedExcludes = ['exclude'] as Set
        Map expectedFilters = [x: 'filter']
        CopyInstruction copyInstruction = new CopyInstructionFactory().createCopyInstruction(
            expectedSourceDir, expectedTargetDir, expectedIncludes, expectedExcludes, expectedFilters)
        assert copyInstruction.sourceDir.is(expectedSourceDir)
        assert copyInstruction.targetDir.is(expectedTargetDir)
        assert copyInstruction.includes.is(expectedIncludes)
        assert copyInstruction.excludes.is(expectedExcludes)
        assert copyInstruction.filters.is(expectedFilters)
    }
}
