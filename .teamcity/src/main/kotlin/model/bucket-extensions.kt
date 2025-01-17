/*
 * Copyright 2020 the original author or authors.
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

package model

import java.util.LinkedList

/**
 * Split a list of elements into nearly even sublist. If an element is too large, largeElementSplitFunction will be used to split the large element into several smaller pieces;
 * if some elements are too small, they will be aggregated by smallElementAggregateFunction.
 *
 * @param list the list to split, must be ordered by size desc
 * @param toIntFunction the function used to map the element to its "size"
 * @param largeElementSplitFunction the function used to further split the large element into smaller pieces
 * @param smallElementAggregateFunction the function used to aggregate tiny elements into a large bucket
 * @param expectedBucketNumber the return value's size should be expectedBucketNumber
 */
fun <T, R> splitIntoBuckets(
    list: LinkedList<T>,
    toIntFunction: (T) -> Int,
    largeElementSplitFunction: (T, Int) -> List<R>,
    smallElementAggregateFunction: (List<T>) -> R,
    expectedBucketNumber: Int,
    maxNumberInBucket: Int,
    noElementSplitFunction: (Int) -> List<R> = { throw IllegalArgumentException("More buckets than things to split") },
    canRunTogether: (T, T) -> Boolean = { _, _ -> true }
): List<R> {
    if (list.isEmpty()) {
        return noElementSplitFunction(expectedBucketNumber)
    }
    if (expectedBucketNumber == 1) {
        return listOf(smallElementAggregateFunction(list))
    }

    val roughSizeOfEachBucket = list.sumOf(toIntFunction) / expectedBucketNumber
    if (roughSizeOfEachBucket == 0) {
        // The elements in the list are so small that they can't even be divided into {expectedBucketNumber}.
        // For example, how do you split [0,0,0,0,0] into 3 buckets?
        // In this case, we simply put the elements into these buckets evenly.
        return list.chunked(list.size / expectedBucketNumber, smallElementAggregateFunction)
    }

    val largestElement = list.removeFirst()!!

    val largestElementSize = toIntFunction(largestElement)

    if (largestElementSize >= roughSizeOfEachBucket) {
        var bucketNumberOfFirstElement =
            determineBucketNumberForLargeElment(
                largestElementSize,
                roughSizeOfEachBucket,
                expectedBucketNumber,
                list,
                toIntFunction
            )

        val bucketsOfFirstElement = largeElementSplitFunction(largestElement, bucketNumberOfFirstElement)
        val bucketsOfRestElements = splitIntoBuckets(
            list,
            toIntFunction,
            largeElementSplitFunction,
            smallElementAggregateFunction,
            expectedBucketNumber - bucketsOfFirstElement.size,
            maxNumberInBucket,
            noElementSplitFunction,
            canRunTogether
        )
        return bucketsOfFirstElement + bucketsOfRestElements
    } else {
        val buckets = arrayListOf(largestElement)
        var restCapacity = roughSizeOfEachBucket - toIntFunction(largestElement)
        while (restCapacity > 0 && list.isNotEmpty() && buckets.size < maxNumberInBucket) {
            val smallestElement = list.findLast { searched -> buckets.all { canRunTogether(it, searched) } } ?: break
            list.remove(smallestElement)
            buckets.add(smallestElement)
            restCapacity -= toIntFunction(smallestElement)
        }
        return listOf(smallElementAggregateFunction(buckets)) + splitIntoBuckets(
            list,
            toIntFunction,
            largeElementSplitFunction,
            smallElementAggregateFunction,
            expectedBucketNumber - 1,
            maxNumberInBucket,
            noElementSplitFunction,
            canRunTogether
        )
    }
}

/**
 * Determine the number of buckets for the first element in the list
 * when it needs to be split into several smaller pieces.
 *
 * The basic idea is:
 * 1. Make sure the rest elements has at least one bucket.
 * 2. Make sure the "roughSizeOfEachBucket" for the rest elements is smaller than the current "roughSizeOfEachBucket".
 */
private fun <T> determineBucketNumberForLargeElment(
    largestElementSize: Int,
    roughSizeOfEachBucket: Int,
    expectedBucketNumber: Int,
    list: LinkedList<T>,
    toIntFunction: (T) -> Int
): Int {
    var bucketNumberOfFirstElement = if (largestElementSize % roughSizeOfEachBucket == 0)
        largestElementSize / roughSizeOfEachBucket
    else
        largestElementSize / roughSizeOfEachBucket + 1

    while (true) {
        if (bucketNumberOfFirstElement == 1) {
            break
        }

        if (expectedBucketNumber - bucketNumberOfFirstElement <= 0) {
            bucketNumberOfFirstElement--
        } else {
            val roughSizeOfEachBucketForRestElements =
                list.sumOf(toIntFunction) / (expectedBucketNumber - bucketNumberOfFirstElement)

            if (roughSizeOfEachBucketForRestElements > roughSizeOfEachBucket) {
                bucketNumberOfFirstElement--
            } else {
                break
            }
        }
    }
    return bucketNumberOfFirstElement
}
