/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.cache.internal;

import com.google.common.base.Preconditions;
import com.google.common.collect.AbstractIterator;
import com.google.common.collect.Iterators;

import javax.annotation.Nonnull;
import java.io.File;
import java.io.FileFilter;
import java.util.ArrayDeque;
import java.util.Collections;
import java.util.Deque;
import java.util.Iterator;

public class SingleDepthFilesFinder implements FilesFinder {
    private final int depth;

    public SingleDepthFilesFinder(int depth) {
        Preconditions.checkArgument(depth > 0, "depth must be > 0: %s", depth);
        this.depth = depth;
    }

    @Override
    public Iterable<File> find(final File baseDir, final FileFilter filter) {
        return new Iterable<File>() {
            @Override
            @Nonnull
            public Iterator<File> iterator() {
                return new SingleDepthFileIterator(baseDir, filter);
            }
        };
    }

    private class SingleDepthFileIterator extends AbstractIterator<File> {

        private final Deque<Iterator<File>> stack = new ArrayDeque<Iterator<File>>();
        private final int targetSize;
        private final FileFilter filter;

        SingleDepthFileIterator(File baseDir, FileFilter filter) {
            stack.push(Iterators.singletonIterator(baseDir));
            this.filter = filter;
            this.targetSize = depth + 1;
        }

        @Override
        protected File computeNext() {
            advanceIfNecessary();
            if (stack.isEmpty()) {
                return endOfData();
            }
            return stack.getLast().next();
        }

        private void advanceIfNecessary() {
            while (!stack.isEmpty() && !hasNextWithCorrectDepth()) {
                if (stack.getLast().hasNext()) {
                    File next = stack.getLast().next();
                    stack.addLast(listFiles(next));
                } else {
                    stack.removeLast();
                }
            }
        }

        private boolean hasNextWithCorrectDepth() {
            return stack.size() == targetSize && stack.getLast().hasNext();
        }

        private Iterator<File> listFiles(File baseDir) {
            File[] files = baseDir.listFiles(filter);
            return files == null ? Collections.<File>emptyIterator() : Iterators.forArray(files);
        }
    }
}
