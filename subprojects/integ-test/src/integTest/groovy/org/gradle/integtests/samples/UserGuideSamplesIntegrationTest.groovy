/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.integtests.samples

import org.junit.Ignore
import org.junit.runner.RunWith

@RunWith(UserGuideSamplesRunner.class)
@Ignore
class UserGuideSamplesIntegrationTest {
    /*
    Important info:

     If you're working in samples area there're gradle tasks that you should know of:
     - gradle intTestImage makes sure that the samples' resources are copied to the right place
     - gradle docs:extractSamples makes sure that samples' info is extracted from XMLs
     - the 'expected' content of the asserting mechanism lives under docs/src/samples/userguideOutput
     - Running:
        ./gradlew intTestImage docs:extractSamples integtest:integTest --tests "*UserGuideSamplesIntegrationTest*"

     Samples are not tested by default. For a sample to be executed and tested, you need to:
     - add a nested <output/> tag to the <sample/> tag
     - use the `args` parameter of the output tag to specify the tasks to be executed
     - optionally set the name of the reference output file (by default, will use [sample id].out)
    */
}
