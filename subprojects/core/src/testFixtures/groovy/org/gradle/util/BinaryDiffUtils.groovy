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

package org.gradle.util

import com.google.common.base.Splitter

class BinaryDiffUtils {
    public static List<String> toHexStrings(byte[] bytes, int bytesPerLine = 16) {
        def sw = new StringWriter()
        bytes.encodeHex().writeTo(sw)
        return Splitter.fixedLength(bytesPerLine * 2).split(sw.toString()).collect { line ->
            Splitter.fixedLength(2).split(line).join(" ")
        }
    }

    public static int levenshteinDistance(byte[] lhs, byte[] rhs) {
        int len0 = lhs.length + 1;
        int len1 = rhs.length + 1;

        // the array of distances
        int[] cost = new int[len0];
        int[] newcost = new int[len0];

        // initial cost of skipping prefix in byte[] s0
        for (int i = 0; i < len0; i++) {
            cost[i] = i
        }

        // dynamically computing the array of distances

        // transformation cost for each letter in s1
        for (int j = 1; j < len1; j++) {
            // initial cost of skipping prefix in byte[] s1
            newcost[0] = j;

            // transformation cost for each letter in s0
            for (int i = 1; i < len0; i++) {
                // matching current letters in both strings
                int match = (lhs[i - 1] == rhs[j - 1]) ? 0 : 1;

                // computing cost for each transformation
                int costReplace = cost[i - 1] + match;
                int costInsert = cost[i] + 1;
                int costDelete = newcost[i - 1] + 1;

                // keep minimum cost
                newcost[i] = Math.min(Math.min(costInsert, costDelete), costReplace);
            }

            // swap cost/newcost arrays
            int[] swap = cost; cost = newcost; newcost = swap;
        }

        // the distance is the cost for transforming all letters in both strings
        return cost[len0 - 1];
    }
}
