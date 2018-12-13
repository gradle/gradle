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

package criterion

import java.lang.Math.sqrt


/**
 * A result of an experiment expressed as a set of observations measured in a unit [U].
 *
 * @param U the unit of measure
 */
abstract class Result<U>(observations: List<U>) {

    init {
        assert(observations.isNotEmpty())
    }

    val points = observations.sortedBy { it.magnitude }

    val median by lazy {
        val middle = points.size / 2
        if (points.size.rem(2) == 0) {
            points.subList(middle - 1, middle + 1).average()
        } else {
            points[middle]
        }
    }

    val mean by lazy {
        points.average()
    }

    val stdDev by lazy {
        sqrt(variance).measure
    }

    val variance by lazy {
        points.map { square(it.magnitude - mean.magnitude) }.average()
    }

    abstract val U.magnitude: Double

    abstract val Double.measure: U

    fun Iterable<U>.average() =
        map { it.magnitude }.average().measure
}


fun square(d: Double) = d * d
