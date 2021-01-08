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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public abstract class IncrementalElement {
    public final OriginalElement original = new OriginalElement();
    public final AlternateElement alternate = new AlternateElement();

    protected abstract List<Transform> getIncrementalChanges();

    /**
     * Transforms the app element into the alternate app element in the given project.
     */
    public void applyChangesToProject(TestFile projectDir) {
        for (Transform transform : getIncrementalChanges()) {
            transform.applyChangesToProject(projectDir);
        }
    }

    /**
     * Writes the source files of the app element to the given project.
     */
    public void writeToProject(TestFile projectDir) {
        original.writeToProject(projectDir);
    }

    public String getSourceSetName() {
        return "main";
    }

    public interface Transform {
        void applyChangesToProject(TestFile projectDir);

        List<SourceFile> getBeforeFiles();

        List<SourceFile> getAfterFiles();
    }

    /**
     * Returns a transform that keep the before element intact.
     */
    protected static Transform preserve(final SourceElement element) {
        return new Transform() {
            @Override
            public void applyChangesToProject(TestFile projectDir) {}

            @Override
            public List<SourceFile> getBeforeFiles() {
                return element.getFiles();
            }

            @Override
            public List<SourceFile> getAfterFiles() {
                return element.getFiles();
            }
        };
    }

    /**
     * Returns a transform that replace the content of the before element with the content of the after element.
     * Both elements must have the same location.
     */
    protected static Transform modify(final SourceElement beforeElement, final SourceElement afterElement) {
        assert hasSameFiles(beforeElement.getFiles(), afterElement.getFiles());
        assert beforeElement.getSourceSetName().equals(afterElement.getSourceSetName());

        return new Transform() {
            @Override
            public void applyChangesToProject(TestFile projectDir) {
                afterElement.writeToProject(projectDir);
            }

            @Override
            public List<SourceFile> getBeforeFiles() {
                return beforeElement.getFiles();
            }

            @Override
            public List<SourceFile> getAfterFiles() {
                return afterElement.getFiles();
            }
        };
    }

    private static boolean hasSameFiles(Collection<SourceFile> beforeFiles, Collection<SourceFile> afterFiles)  {
        if (beforeFiles.size() != afterFiles.size()) {
            return false;
        }

        for (SourceFile beforeFile : beforeFiles) {
            boolean found = false;
            for (SourceFile afterFile : afterFiles) {
                if (beforeFile.getName().equals(afterFile.getName()) && beforeFile.getPath().equals(afterFile.getPath())) {
                    found = true;
                    break;
                }
            }

            if (!found) {
                return false;
            }
        }

        return true;
    }

    /**
     * Returns a transform that delete the before element.
     */
    protected static Transform delete(final SourceElement beforeElement) {
        final String sourceSetName = beforeElement.getSourceSetName();
        final List<SourceFile> beforeFiles = beforeElement.getFiles();

        return new Transform() {
            @Override
            public void applyChangesToProject(TestFile projectDir) {
                for (SourceFile beforeFile : beforeFiles) {
                    TestFile file = projectDir.file(beforeFile.withPath("src/" + sourceSetName));
                    file.assertExists();

                    file.delete();
                }
            }

            @Override
            public List<SourceFile> getBeforeFiles() {
                return beforeElement.getFiles();
            }

            @Override
            public List<SourceFile> getAfterFiles() {
                return Collections.emptyList();
            }
        };
    }

    /**
     * Returns a transform that add the after element.
     */
    protected static Transform add(final SourceElement afterElement) {
        return new Transform() {
            @Override
            public void applyChangesToProject(TestFile projectDir) {
                afterElement.writeToProject(projectDir);
            }

            @Override
            public List<SourceFile> getBeforeFiles() {
                return Collections.emptyList();
            }

            @Override
            public List<SourceFile> getAfterFiles() {
                return afterElement.getFiles();
            }
        };
    }

    /**
     * Generic transform class that rename the source of the before element.
     */
    protected static abstract class AbstractRenameTransform implements Transform {
        public static final String DEFAULT_RENAME_PREFIX = "renamed-";
        private final SourceFile sourceFile;
        private final SourceFile destinationFile;
        private final SourceElement beforeElement;

        AbstractRenameTransform(SourceFile sourceFile, SourceFile destinationFile, SourceElement beforeElement) {
            this.sourceFile = sourceFile;
            this.destinationFile = destinationFile;
            this.beforeElement = beforeElement;
        }

        @Override
        public void applyChangesToProject(TestFile projectDir) {
            String sourceSetName = beforeElement.getSourceSetName();
            TestFile file = projectDir.file(sourceFile.withPath("src/" + sourceSetName));

            file.assertExists();

            file.renameTo(projectDir.file(destinationFile.withPath("src/" + sourceSetName)));
        }

        @Override
        public List<SourceFile> getBeforeFiles() {
            return beforeElement.getFiles();
        }
    }

    private class OriginalElement extends SourceElement {
        @Override
        public List<SourceFile> getFiles() {
            List<SourceFile> result = new ArrayList<SourceFile>();
            for (Transform transform : getIncrementalChanges()) {
                result.addAll(transform.getBeforeFiles());
            }
            return result;
        }

        @Override
        public String getSourceSetName() {
            return IncrementalElement.this.getSourceSetName();
        }
    }

    private class AlternateElement extends SourceElement {
        @Override
        public List<SourceFile> getFiles() {
            List<SourceFile> result = new ArrayList<SourceFile>();
            for (Transform transform : getIncrementalChanges()) {
                result.addAll(transform.getAfterFiles());
            }
            return result;
        }

        @Override
        public String getSourceSetName() {
            return IncrementalElement.this.getSourceSetName();
        }
    }
}
