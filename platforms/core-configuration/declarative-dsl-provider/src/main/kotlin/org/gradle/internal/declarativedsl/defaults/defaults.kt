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

package org.gradle.internal.declarativedsl.defaults

import org.gradle.internal.declarativedsl.analysis.AssignmentRecord
import org.gradle.internal.declarativedsl.analysis.DataAdditionRecord
import org.gradle.internal.declarativedsl.analysis.NestedObjectAccessRecord
import org.gradle.plugin.software.internal.ModelDefault
import org.gradle.plugin.software.internal.ModelDefault.Visitor


/**
 * A convention that applies a property assignment operation (e.g. foo = "bar").
 */
class AssignmentRecordDefault(private val assignmentRecord: AssignmentRecord) :
    ModelDefault<Visitor<AssignmentRecord>> {
    override fun visit(visitor: Visitor<AssignmentRecord>) {
        visitor.apply(assignmentRecord)
    }
}


/**
 * A convention that applies a data addition operation (e.g. addFoo("bar")).
 */
class AdditionRecordDefault(private val dataAdditionRecord: DataAdditionRecord) :
    ModelDefault<Visitor<DataAdditionRecord>> {
    override fun visit(visitor: Visitor<DataAdditionRecord>) {
        visitor.apply(dataAdditionRecord)
    }
}


/**
 * A convention that applies a nested object access operation (e.g. foo { }).
 */
class NestedObjectAccessDefault(private val nestedObjectAccessRecord: NestedObjectAccessRecord) :
    ModelDefault<Visitor<NestedObjectAccessRecord>> {
    override fun visit(visitor: Visitor<NestedObjectAccessRecord>) {
        visitor.apply(nestedObjectAccessRecord)
    }
}
