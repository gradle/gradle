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
import org.gradle.internal.declarativedsl.analysis.DataAdditionRecord
import org.gradle.internal.declarativedsl.analysis.NestedObjectAccessRecord
import org.gradle.plugin.software.internal.Convention
import org.gradle.plugin.software.internal.ConventionReceiver


/**
 * A convention that applies a property assignment operation (e.g. foo = "bar").
 */
class AssignmentRecordConvention(private val assignmentRecord: AssignmentRecord) :
    Convention<AssignmentRecordConventionReceiver> {
    override fun apply(receiver: AssignmentRecordConventionReceiver) {
        receiver.receive(assignmentRecord)
    }
}


/**
 * A convention that applies a data addition operation (e.g. addFoo("bar")).
 */
class AdditionRecordConvention(private val dataAdditionRecord: DataAdditionRecord) :
    Convention<AdditionRecordConventionReceiver> {
    override fun apply(receiver: AdditionRecordConventionReceiver) {
        receiver.receive(dataAdditionRecord)
    }
}


/**
 * A convention that applies a nested object access operation (e.g. foo { }).
 */
class NestedObjectAccessConvention(private val nestedObjectAccessRecord: NestedObjectAccessRecord) :
    Convention<NestedObjectAccessRecordConventionReceiver> {
    override fun apply(receiver: NestedObjectAccessRecordConventionReceiver) {
        receiver.receive(nestedObjectAccessRecord)
    }
}


fun interface AssignmentRecordConventionReceiver : ConventionReceiver {
    fun receive(assignmentRecord: AssignmentRecord)
}


fun interface AdditionRecordConventionReceiver : ConventionReceiver {
    fun receive(additionRecord: DataAdditionRecord)
}


fun interface NestedObjectAccessRecordConventionReceiver : ConventionReceiver {
    fun receive(additionRecord: NestedObjectAccessRecord)
}
