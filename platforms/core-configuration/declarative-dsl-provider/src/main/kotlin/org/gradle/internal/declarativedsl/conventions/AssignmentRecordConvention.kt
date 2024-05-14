/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.internal.declarativedsl.conventions

import org.gradle.internal.declarativedsl.analysis.AssignmentRecord
import prg.gradle.declarative.dsl.model.conventions.Convention
import prg.gradle.declarative.dsl.model.conventions.ConventionReceiver

class AssignmentRecordConvention(private val assignmentRecord: AssignmentRecord) : Convention<AssignmentRecordConventionReceiver> {
    override fun apply(receiver: AssignmentRecordConventionReceiver) {
        receiver.receive(assignmentRecord)
    }
}

interface AssignmentRecordConventionReceiver : ConventionReceiver {
    fun receive(assignmentRecord: AssignmentRecord)
}
