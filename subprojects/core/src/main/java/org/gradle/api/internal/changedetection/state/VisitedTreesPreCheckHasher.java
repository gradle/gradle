/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.changedetection.state;

import org.gradle.api.internal.file.BufferedStreamingHasher;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.serialize.Encoder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class VisitedTreesPreCheckHasher {
    public VisitedTreesPreCheckHasher() {
    }

    int calculatePreCheckHash(Collection<VisitedTree> visitedTrees) {
        BufferedStreamingHasher hasher = new BufferedStreamingHasher();
        Encoder encoder = hasher.getEncoder();
        try {
            List<VisitedTree> sortedTrees = new ArrayList<VisitedTree>(visitedTrees);
            Collections.sort(sortedTrees, DefaultVisitedTree.VisitedTreeComparator.INSTANCE);
            for (VisitedTree tree : sortedTrees) {
                if (tree.getAbsolutePath() != null) {
                    encoder.writeString(tree.getAbsolutePath());
                }
                if (tree.getPatternSet() != null) {
                    encoder.writeInt(tree.getPatternSet().hashCode());
                }
                encoder.writeInt(tree.getEntries().size());
                encoder.writeInt(tree.calculatePreCheckHash());
            }
            return hasher.checksum();
        } catch (IOException e) {
            throw UncheckedException.throwAsUncheckedException(e);
        }
    }
}
