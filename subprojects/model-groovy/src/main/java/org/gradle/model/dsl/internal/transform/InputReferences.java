/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.model.dsl.internal.transform;

import com.google.common.collect.Lists;

import java.util.Arrays;
import java.util.List;

public class InputReferences {
    private final List<String> relativePaths = Lists.newArrayList();
    private final List<Integer> relativePathLineNumbers = Lists.newArrayList();
    private final List<String> absolutePaths = Lists.newArrayList();
    private final List<Integer> absolutePathLineNumbers = Lists.newArrayList();

    public List<String> getAbsolutePaths() {
        return absolutePaths;
    }

    public List<Integer> getAbsolutePathLineNumbers() {
        return absolutePathLineNumbers;
    }

    public List<String> getRelativePaths() {
        return relativePaths;
    }

    public List<Integer> getRelativePathLineNumbers() {
        return relativePathLineNumbers;
    }

    public void relativePath(String path, int lineNumber) {
        relativePaths.add(path);
        relativePathLineNumbers.add(lineNumber);
    }

    public void absolutePath(String path, int lineNumber) {
        absolutePaths.add(path);
        absolutePathLineNumbers.add(lineNumber);
    }

    public boolean isEmpty() {
        return relativePaths.isEmpty() && absolutePaths.isEmpty();
    }

    public void absolutePaths(String[] paths, int[] lineNumbers) {
        absolutePaths.addAll(Arrays.asList(paths));
        for (int lineNumber : lineNumbers) {
            absolutePathLineNumbers.add(lineNumber);
        }
    }

    public void relativePaths(String[] paths, int[] lineNumbers) {
        relativePaths.addAll(Arrays.asList(paths));
        for (int lineNumber : lineNumbers) {
            relativePathLineNumbers.add(lineNumber);
        }
    }
}
