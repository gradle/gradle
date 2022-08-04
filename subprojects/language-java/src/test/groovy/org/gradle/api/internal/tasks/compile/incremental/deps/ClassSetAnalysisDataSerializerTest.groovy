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
import org.gradle.api.internal.cache.StringInterner
import org.gradle.api.internal.tasks.compile.incremental.serialization.HierarchicalNameSerializer
import org.gradle.internal.hash.HashCode
import org.gradle.internal.hash.TestHashCodes
import org.gradle.internal.serialize.InputStreamBackedDecoder
import org.gradle.internal.serialize.OutputStreamBackedEncoder
import spock.lang.Specification
import spock.lang.Subject

import static org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentsSet.dependencyToAll
import static org.gradle.api.internal.tasks.compile.incremental.compilerapi.deps.DependentsSet.dependentClasses

class ClassSetAnalysisDataSerializerTest extends Specification {

    HashCode hash = TestHashCodes.hashCodeFrom(0)
    @Subject serializer = new ClassSetAnalysisData.Serializer({ new HierarchicalNameSerializer(new StringInterner())})

    def "serializes"() {
        def data = new ClassSetAnalysisData(["A": hash, "B": hash, "C": hash, "D": hash],
            ["A": dependentClasses(["B", "C"] as Set, [] as Set), "B": dependentClasses(["C"] as Set, [] as Set), "C": dependentClasses([] as Set, [] as Set), "D": dependencyToAll("reason"),],
            [C: new IntOpenHashSet([1, 2]) as IntSet, D: IntSets.EMPTY_SET]
            ,"Because"
        )
        def os = new ByteArrayOutputStream()
        def e = new OutputStreamBackedEncoder(os)

        when:
        serializer.write(e, data)
        ClassSetAnalysisData read = serializer.read(new InputStreamBackedDecoder(new ByteArrayInputStream(os.toByteArray())))

        then:
        read.dependents.keySet() == data.dependents.keySet()

        ["A", "B", "C"].each {
            assert read.dependents[it].privateDependentClasses == data.dependents[it].privateDependentClasses
            assert read.dependents[it].accessibleDependentClasses == data.dependents[it].accessibleDependentClasses
            assert read.dependents[it].dependencyToAll == data.dependents[it].dependencyToAll
        }

        read.dependents["D"].dependencyToAll
        read.classesToConstants == [C: [1,2] as Set, D: [] as Set]
        read.fullRebuildCause == "Because"
    }
}
