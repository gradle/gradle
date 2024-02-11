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

import java.util.ArrayList;
import java.util.List;

public abstract class IncrementalCppElement extends IncrementalElement {
    /**
     * Returns a transform that rename the before element to {@code renamed-} followed by the original name.
     */
    protected static Transform rename(CppSourceFileElement beforeElement) {
        return rename(beforeElement, AbstractRenameTransform.DEFAULT_RENAME_PREFIX);
    }

    protected static Transform rename(final CppSourceFileElement beforeElement, String renamePrefix) {
        SourceFile beforeFile = beforeElement.getSource().getSourceFile();
        final SourceFile afterFile = new SourceFile(beforeFile.getPath(), renamePrefix + beforeFile.getName(), beforeFile.getContent());

        return new AbstractRenameTransform(beforeFile, afterFile, beforeElement) {
            @Override
            public List<SourceFile> getAfterFiles() {
                List<SourceFile> result = new ArrayList<SourceFile>();
                result.addAll(beforeElement.getHeaders().getFiles());
                result.add(afterFile);
                return result;
            }
        };
    }
}
