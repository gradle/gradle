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

package org.gradle.initialization

import spock.lang.Specification
import spock.lang.Unroll

class ParallelOptionsCommandLineConverterTest extends Specification {

    final static int NUM_OF_PROCS = Runtime.getRuntime().availableProcessors()
    final static int N = 3
    final static int M = 5

    @Unroll("check combinations using #args")
    public void checkCombinationsOfWorkersAndParallelOptions(List args, int maxWorkers, int parallelThreads, boolean isParallel) {
        given:
        CommandLineConverterTestSupport commandLineTester = new CommandLineConverterTestSupport()

        commandLineTester.expectedMaxWorkersCount = maxWorkers
        commandLineTester.expectedParallelExecutorCount = parallelThreads
        commandLineTester.expectedParallelProjectExecution = isParallel

        expect:
        commandLineTester.checkConversion(*args)

        where:
        args                                          | maxWorkers   | parallelThreads | isParallel
        []                                            | NUM_OF_PROCS | 0               | false
        ["--parallel"]                                | NUM_OF_PROCS | NUM_OF_PROCS    | true
        ["--parallel-threads=$N"]                     | N            | N               | true
        ["--max-workers=$N"]                          | N            | 0               | false
        ["--parallel", "--max-workers=$N"]            | N            | N               | true
        ["--parallel-threads=$N", "--max-workers=$M"] | M            | M               | true
        ["--max-workers=$N", "--parallel-threads=$M"] | M            | M               | true
        ["--parallel-threads=-1"]                     | NUM_OF_PROCS | NUM_OF_PROCS    | true
        ["--parallel-threads=0"]                      | NUM_OF_PROCS | 0               | false
        ["--parallel-threads=1"]                      | 1            | 1               | true
        ["--parallel", "--max-workers=1"]             | 1            | 1               | true
    }

}
