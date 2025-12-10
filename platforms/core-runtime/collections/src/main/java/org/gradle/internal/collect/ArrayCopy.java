/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.internal.collect;

import java.util.Arrays;

/// Different array copying operations required by the persistent collection
/// implementations.
///
/// In general, these should be as efficient as possible, contain
/// no branching code, and minimize [System] calls.
/// In practice, it gets [complicated][#insertAtPushingRight(int, Object\[\], Object\[\], int, int, int)].
final class ArrayCopy {

    static final Object[] EMPTY_ARRAY = new Object[0];

    static Object[] append(Object[] array, Object newElement) {
        Object[] newArray = Arrays.copyOf(array, array.length + 1);
        newArray[array.length] = newElement;
        return newArray;
    }

    static Object[] append(Object[] array, Object e1, Object e2) {
        int length = array.length;
        Object[] newArray = Arrays.copyOf(array, length + 2);
        newArray[length] = e1;
        newArray[length + 1] = e2;
        return newArray;
    }

    static Object[] replaceAt(int index, Object[] array, Object newElement) {
        Object[] newArray = Arrays.copyOf(array, array.length);
        newArray[index] = newElement;
        return newArray;
    }

    static Object[] insertAt(int index, Object[] array, Object newElement) {
        Object[] newArray = Arrays.copyOf(array, array.length + 1);
        System.arraycopy(array, index, newArray, index + 1, array.length - index);
        newArray[index] = newElement;
        return newArray;
    }

    static Object[] insertAt(int index, Object[] array, Object e1, Object e2) {
        Object[] newArray = Arrays.copyOf(array, array.length + 2);
        System.arraycopy(array, index, newArray, index + 2, array.length - index);
        newArray[index] = e1;
        newArray[index + 1] = e2;
        return newArray;
    }

    // 🤔 Note: This has different behavior based on payload (0 vs 1).
    // For payload=0: array length stays the same (insert + remove 1 element)
    // For payload=1: array length decreases by 1 (insert 1 + remove 2 elements = net -1)
    // This asymmetry is because for maps we're removing a key-value pair (2 elements)
    // but only inserting the sub-node (1 element).
    static Object[] insertAtPushingLeft(int index, Object[] array, Object newElement, int leftIndexToOverwrite, int payload) {
        assert index >= leftIndexToOverwrite;
        assert payload == 0 || payload == 1;
        if (payload == 0) {
            Object[] newArray = Arrays.copyOf(array, array.length);
            System.arraycopy(array, leftIndexToOverwrite + 1, newArray, leftIndexToOverwrite, index - leftIndexToOverwrite);
            newArray[index] = newElement;
            return newArray;
        } else {
            int length = array.length;
            Object[] newArray = Arrays.copyOf(array, length - 1);
            // Move the middle block [left+2 .. index] left by 2 positions, if any
            int middleLen = index - leftIndexToOverwrite - 1;
            System.arraycopy(array, leftIndexToOverwrite + 2, newArray, leftIndexToOverwrite, middleLen);
            // Move the tail block [index+1 .. end] left by 1 position, if any
            int tailLen = length - index - 1;
            System.arraycopy(array, index + 1, newArray, index, tailLen);
            newArray[index - 1] = newElement;
            return newArray;
        }
    }

    static Object[] insertAtPushingRight(int index, Object[] original, Object[] dataSrc, int dataSrcIndex, int dataLength, int rightIndexToOverwrite) {
        assert index <= rightIndexToOverwrite;
        assert dataLength >= 0;
        assert dataSrcIndex >= 0 && dataSrcIndex + dataLength <= dataSrc.length;

        int originalLength = original.length;

        // New length grows by dataLength, but we remove one element at rightIndexToOverwrite
        int newLength = originalLength + dataLength - 1;
        Object[] newArray = new Object[newLength];

        // 1. Copy head [0 .. index-1]
        System.arraycopy(original, 0, newArray, 0, index);

        // 2. Insert block from dataSrc[dataSrcIndex .. dataSrcIndex+dataLength-1] at position index
        System.arraycopy(dataSrc, dataSrcIndex, newArray, index, dataLength);

        // 3. Move middle block [index .. rightIndexToOverwrite-1] to the right by dataLength positions
        int middleLen = rightIndexToOverwrite - index;
        System.arraycopy(original, index, newArray, index + dataLength, middleLen);

        // 4. Copy tail [rightIndexToOverwrite+1 .. end] after the middle, shifted by (dataLength - 1)
        int tailSrcPos = rightIndexToOverwrite + 1;
        int tailLen = originalLength - tailSrcPos;
        int tailDestPos = rightIndexToOverwrite + dataLength;
        System.arraycopy(original, tailSrcPos, newArray, tailDestPos, tailLen);

        return newArray;
    }

    static Object[] insertAtPushingRight(int index, Object[] array, Object newElement, int rightIndexToOverwrite) {
        assert index <= rightIndexToOverwrite;
        Object[] newArray = Arrays.copyOf(array, array.length);
        int len = rightIndexToOverwrite - index;
        System.arraycopy(array, index, newArray, index + 1, len);
        newArray[index] = newElement;
        return newArray;
    }

    static Object[] removeAt(int index, Object[] array, int count) {
        int newLen = array.length - count;
        Object[] newArray = new Object[newLen];
        System.arraycopy(array, 0, newArray, 0, index);
        int tailLen = newLen - index;
        System.arraycopy(array, index + count, newArray, index, tailLen);
        return newArray;
    }
}
