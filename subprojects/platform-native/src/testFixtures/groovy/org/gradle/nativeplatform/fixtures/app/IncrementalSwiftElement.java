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

package org.gradle.nativeplatform.fixtures.app;

import org.gradle.integtests.fixtures.SourceFile;
import org.gradle.test.fixtures.file.TestFile;

import java.util.Arrays;
import java.util.List;

public abstract class IncrementalSwiftElement extends IncrementalElement {
    public abstract String getModuleName();

    /**
     * Returns a transform that replace the content of the before element with the content of the after element.
     * Both elements must have the same location.
     */
    protected static Transform modify(final SourceFileElement beforeElement, SourceFileElement afterElement) {
        assert beforeElement.getFiles().size() == 1;
        assert afterElement.getFiles().size() == 1;
        assert beforeElement.getSourceSetName().equals(afterElement.getSourceSetName());
        final String sourceSetName = beforeElement.getSourceSetName();
        final SourceFile beforeFile = beforeElement.getSourceFile();
        final SourceFile afterFile = afterElement.getSourceFile();
        assert beforeFile.getPath().equals(afterFile.getPath());
        assert beforeFile.getName().equals(afterFile.getName());
        assert !beforeFile.getContent().equals(afterFile.getContent());

        return new Transform() {
            @Override
            public void applyChangesToProject(TestFile projectDir) {
                TestFile file = projectDir.file(beforeFile.withPath("src/" + sourceSetName));
                file.assertExists();

                file.write(afterFile.getContent());
            }

            @Override
            public List<SourceFile> getBeforeFiles() {
                return Arrays.asList(beforeFile);
            }

            @Override
            public List<SourceFile> getAfterFiles() {
                return Arrays.asList(afterFile);
            }
        };
    }

    /**
     * Returns a transform that rename the before element to {@code renamed-} followed by the original name.
     */
    protected static Transform rename(SourceFileElement beforeElement) {
        return rename(beforeElement, AbstractRenameTransform.DEFAULT_RENAME_PREFIX);
    }

    protected static Transform rename(SourceFileElement beforeElement, String renamePrefix) {
        assert beforeElement.getFiles().size() == 1;
        SourceFile beforeFile = beforeElement.getSourceFile();
        final SourceFile afterFile = new SourceFile(beforeFile.getPath(), renamePrefix + beforeFile.getName(), beforeFile.getContent());

        return new AbstractRenameTransform(beforeFile, afterFile, beforeElement) {
            @Override
            public List<SourceFile> getAfterFiles() {
                return Arrays.asList(afterFile);
            }
        };
    }
}
