/*
 * Copyright 2014 the original author or authors.
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

package org.gradle.api.internal.tasks.compile.incremental.deps

import it.unimi.dsi.fastutil.ints.IntOpenHashSet
import it.unimi.dsi.fastutil.ints.IntSet
import it.unimi.dsi.fastutil.ints.IntSets
import org.gradle.internal.serialize.InputStreamBackedDecoder
import org.gradle.internal.serialize.OutputStreamBackedEncoder
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet.dependencyToAll
import static org.gradle.api.internal.tasks.compile.incremental.deps.DependentsSet.dependents

class ClassSetAnalysisDataSerializerTest extends Specification {

    @Subject serializer = new ClassSetAnalysisData.Serializer()

    def "serializes"() {
        def data = new ClassSetAnalysisData(
            ["A.class": "A", "B.class": "B"],
            ["A": dependents("B", "C"), "B": dependents("C"), "C": dependents(), "D": dependencyToAll(),],
            [C: new IntOpenHashSet([1, 2]) as IntSet, D: IntSets.EMPTY_SET]
            ,
            ['A': ['SA'] as Set, B: ['SB1', 'SB2'] as Set], dependents("Aggregate"), "Because"
        )
        def os = new ByteArrayOutputStream()
        def e = new OutputStreamBackedEncoder(os)

        when:
        serializer.write(e, data)
        ClassSetAnalysisData read = serializer.read(new InputStreamBackedDecoder(new ByteArrayInputStream(os.toByteArray())))

        then:
        read.dependents.keySet() == data.dependents.keySet()

        ["A", "B", "C"].each {
            assert read.dependents[it].dependentClasses == data.dependents[it].dependentClasses
            assert read.dependents[it].dependencyToAll == data.dependents[it].dependencyToAll
        }

        read.dependents["D"].dependencyToAll
        read.dependentsOnAll.dependentClasses == ["Aggregate"] as Set
        !read.dependentsOnAll.dependencyToAll
        read.filePathToClassName == ["A.class": "A", "B.class": "B"]
        read.classesToConstants == [C: [1,2] as Set, D: [] as Set]
        read.classesToChildren == ['A': ['SA'] as Set, B: ['SB1', 'SB2'] as Set]
        read.fullRebuildCause == "Because"
    }
}
