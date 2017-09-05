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

import com.google.common.collect.Sets;
import org.gradle.integtests.fixtures.SourceFile;
import org.gradle.test.fixtures.file.TestFile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

public abstract class IncrementalCppElement extends IncrementalElement {
    @Override
    protected Set<String> intermediateFilenames(SourceFile sourceFile) {
        if (sourceFile.getName().endsWith(".h")) {
            return Collections.emptySet();
        }
        return Sets.newHashSet(sourceFile.getName().replace(".cpp", ".o"));
    }

    /**
     * Returns a transform that rename the before element to {@code renamed-} followed by the original name.
     */
    protected static Transform rename(CppSourceFileElement beforeElement) {
        return rename(beforeElement, "renamed-");
    }

    protected static Transform rename(final CppSourceFileElement beforeElement, String renamePrefix) {
        final String sourceSetName = beforeElement.getSourceSetName();
        final SourceFile beforeFile = beforeElement.getSource().getSourceFile();
        final SourceFile afterFile = new SourceFile(beforeFile.getPath(), renamePrefix + beforeFile.getName(), beforeFile.getContent());

        return new Transform() {
            @Override
            public void applyChangesToProject(TestFile projectDir) {
                TestFile file = projectDir.file(beforeFile.withPath("src/" + sourceSetName));

                file.assertExists();

                file.renameTo(projectDir.file(afterFile.withPath("src/" + sourceSetName)));
            }

            @Override
            public List<SourceFile> getBeforeFiles() {
                return beforeElement.getFiles();
            }

            @Override
            public List<SourceFile> getAfterFiles() {
                List<SourceFile> result = new ArrayList<SourceFile>();
                result.addAll(beforeElement.getPublicHeaders().getFiles());
                result.addAll(beforeElement.getPrivateHeaders().getFiles());
                result.add(afterFile);
                return result;
            }
        };
    }
}
