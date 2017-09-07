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

import org.gradle.api.internal.file.TestFiles;
import org.gradle.integtests.fixtures.SourceFile;
import org.gradle.nativeplatform.internal.CompilerOutputFileNamingScheme;
import org.gradle.test.fixtures.file.TestFile;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
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

    public List<String> getExpectedIntermediateDescendants() {
        return toExpectedIntermediateDescendants(original);
    }

    public List<String> getExpectedAlternateIntermediateDescendants() {
        return toExpectedIntermediateDescendants(alternate);
    }

    protected abstract List<String> toExpectedIntermediateDescendants(SourceElement sourceElement);

    protected String getIntermediateRelativeFilePath(String sourceSetName, SourceFile sourceFile, String fileSuffix) {
        File baseDir = TestFile.listRoots()[0];  // Pick the first root as the virtual base for the source file
        File outputFile = new CompilerOutputFileNamingScheme(TestFiles.resolver(baseDir))
            .withObjectFileNameSuffix(fileSuffix)
            .withOutputBaseFolder(new File(""))
            .map(new File(baseDir, "src/" + sourceSetName + "/" + sourceFile.getPath() + "/" + sourceFile.getName()));
        return outputFile.getPath().substring(baseDir.getPath().length());
    }

    public interface Transform {
        void applyChangesToProject(TestFile projectDir);

        List<SourceFile> getBeforeFiles();

        List<SourceFile> getAfterFiles();
    }

    /**
     * Returns a transform that keep the before element intact.
     */
    protected static Transform preserve(final SourceFileElement element) {
        assert element.getFiles().size() == 1;

        return new Transform() {
            @Override
            public void applyChangesToProject(TestFile projectDir) {}

            @Override
            public List<SourceFile> getBeforeFiles() {
                return Arrays.asList(element.getSourceFile());
            }

            @Override
            public List<SourceFile> getAfterFiles() {
                return Arrays.asList(element.getSourceFile());
            }
        };
    }

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
     * Returns a transform that delete the before element.
     */
    protected static Transform delete(SourceFileElement beforeElement) {
        assert beforeElement.getFiles().size() == 1;
        final String sourceSetName = beforeElement.getSourceSetName();
        final SourceFile beforeFile = beforeElement.getSourceFile();

        return new Transform() {
            @Override
            public void applyChangesToProject(TestFile projectDir) {
                TestFile file = projectDir.file(beforeFile.withPath("src/" + sourceSetName));
                file.assertExists();

                file.delete();
            }

            @Override
            public List<SourceFile> getBeforeFiles() {
                return Arrays.asList(beforeFile);
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
    protected static Transform add(SourceFileElement afterElement) {
        assert afterElement.getFiles().size() == 1;
        final String sourceSetName = afterElement.getSourceSetName();
        final SourceFile afterFile = afterElement.getSourceFile();


        return new Transform() {
            @Override
            public void applyChangesToProject(TestFile projectDir) {
                TestFile file = projectDir.file(afterFile.withPath("src/" + sourceSetName));

                file.assertDoesNotExist();

                afterFile.writeToDir(projectDir);
            }

            @Override
            public List<SourceFile> getBeforeFiles() {
                return Collections.emptyList();
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
        return rename(beforeElement, "renamed-");
    }

    protected static Transform rename(SourceFileElement beforeElement, String renamePrefix) {
        assert beforeElement.getFiles().size() == 1;
        final String sourceSetName = beforeElement.getSourceSetName();
        final SourceFile beforeFile = beforeElement.getSourceFile();
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
                return Arrays.asList(beforeFile);
            }

            @Override
            public List<SourceFile> getAfterFiles() {
                return Arrays.asList(afterFile);
            }
        };
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

        public List<String> getExpectedIntermediateDescendants() {
            return IncrementalElement.this.getExpectedIntermediateDescendants();
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

        public List<String> getExpectedIntermediateDescendants() {
            return IncrementalElement.this.getExpectedAlternateIntermediateDescendants();
        }
    }
}
