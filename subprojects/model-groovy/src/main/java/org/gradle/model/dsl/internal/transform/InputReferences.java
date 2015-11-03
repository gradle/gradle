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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class InputReferences {
    private final List<String> ownPaths = Lists.newArrayList();
    private final List<Integer> ownLineNumbers = Lists.newArrayList();
    private final List<String> nestedPaths = Lists.newArrayList();
    private final List<Integer> nestedLineNumbers = Lists.newArrayList();

    public List<String> getOwnPaths() {
        return ownPaths;
    }

    public List<Integer> getOwnPathLineNumbers() {
        return ownLineNumbers;
    }

    public void reference(String path, int lineNumber) {
        ownPaths.add(path);
        ownLineNumbers.add(lineNumber);
    }

    public boolean isEmpty() {
        return ownPaths.isEmpty() && nestedPaths.isEmpty();
    }

    public List<String> getNestedPaths() {
        return nestedPaths;
    }

    public List<Integer> getNestedPathLineNumbers() {
        return nestedLineNumbers;
    }

    public void addNestedReferences(InputReferences inputReferences) {
        nestedPaths.addAll(inputReferences.getOwnPaths());
        nestedLineNumbers.addAll(inputReferences.getOwnPathLineNumbers());
        nestedPaths.addAll(inputReferences.getNestedPaths());
        nestedLineNumbers.addAll(inputReferences.getNestedPathLineNumbers());
    }

    public void ownPaths(String[] paths, int[] lineNumbers) {
        ownPaths.addAll(Arrays.asList(paths));
        for (int lineNumber : lineNumbers) {
            ownLineNumbers.add(lineNumber);
        }
    }

    public void nestedPaths(String[] paths, int[] lineNumbers) {
        nestedPaths.addAll(Arrays.asList(paths));
        for (int lineNumber : lineNumbers) {
            nestedLineNumbers.add(lineNumber);
        }
    }

    public List<String> getAllPaths() {
        List<String> result = new ArrayList<String>(ownPaths.size() + nestedPaths.size());
        result.addAll(ownPaths);
        result.addAll(nestedPaths);
        return result;
    }

    public List<Integer> getAllPathLineNumbers() {
        List<Integer> result = new ArrayList<Integer>(ownLineNumbers.size() + nestedLineNumbers.size());
        result.addAll(ownLineNumbers);
        result.addAll(nestedLineNumbers);
        return result;
    }
}
