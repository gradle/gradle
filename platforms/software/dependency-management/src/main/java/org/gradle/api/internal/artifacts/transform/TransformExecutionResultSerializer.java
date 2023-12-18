/*
 * Copyright 2021 the original author or authors.
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

package org.gradle.api.internal.artifacts.transform;

import org.gradle.api.UncheckedIOException;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.gradle.internal.UncheckedException.unchecked;

public class TransformExecutionResultSerializer {
    private static final String INPUT_FILE_PATH_PREFIX = "i/";
    private static final String OUTPUT_FILE_PATH_PREFIX = "o/";

    public void writeToFile(File target, TransformExecutionResult result) {
        List<String> resultFileContents = new ArrayList<>(result.size());

        result.visitOutputs(new TransformExecutionResult.OutputVisitor() {
            @Override
            public void visitEntireInputArtifact() {
                resultFileContents.add(INPUT_FILE_PATH_PREFIX);
            }

            @Override
            public void visitPartOfInputArtifact(String relativePath) {
                resultFileContents.add(INPUT_FILE_PATH_PREFIX + relativePath);
            }

            @Override
            public void visitProducedOutput(String relativePath) {
                resultFileContents.add(OUTPUT_FILE_PATH_PREFIX + relativePath);
            }
        });
        unchecked(() -> Files.write(target.toPath(), resultFileContents));
    }

    public TransformExecutionResult readResultsFile(File resultsFile) {
        Path transformerResultsPath = resultsFile.toPath();
        try {
            TransformExecutionResult.Builder builder = TransformExecutionResult.builder();
            List<String> paths = Files.readAllLines(transformerResultsPath, StandardCharsets.UTF_8);
            for (String path : paths) {
                if (path.startsWith(OUTPUT_FILE_PATH_PREFIX)) {
                    builder.addProducedOutput(path.substring(2));
                } else if (path.startsWith(INPUT_FILE_PATH_PREFIX)) {
                    String relativePathString = path.substring(2);
                    if (relativePathString.isEmpty()) {
                        builder.addEntireInputArtifact();
                    } else {
                        builder.addPartOfInputArtifact(relativePathString);
                    }
                } else {
                    throw new IllegalStateException("Cannot parse result path string: " + path);
                }
            }
            return builder.build();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
