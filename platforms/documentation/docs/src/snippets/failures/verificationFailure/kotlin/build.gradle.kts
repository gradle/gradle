/*
 * Copyright 2023 the original author or authors.
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

// tag::verification-failure[]
val process = tasks.register("process") {
    val outputFile = layout.buildDirectory.file("processed.log")
    outputs.files(outputFile) // <1>

    doLast {
        val logFile = outputFile.get().asFile
        logFile.appendText("Step 1 Complete.") // <2>
        throw VerificationException("Process failed!") // <3>
        logFile.appendText("Step 2 Complete.") // <4>
    }
}

tasks.register("postProcess") {
    inputs.files(process) // <5>

    doLast {
        println("Results: ${inputs.files.singleFile.readText()}") // <6>
    }
}
// end::verification-failure[]
