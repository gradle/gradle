/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.instantiation.generator

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.provider.ListProperty
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.Exec
import org.gradle.cache.internal.TestCrossBuildInMemoryCacheFactory
import org.gradle.internal.instantiation.PropertyRoleAnnotationHandler
import spock.lang.Specification

import javax.inject.Inject

/**
 * Timing test for AsmBackedClassGenerator to measure per-phase cost
 * on realistic Gradle types. Check /tmp/classgen_timing.log for results.
 */
class AsmBackedClassGeneratorTimingTest extends Specification {

    ClassGenerator generator = AsmBackedClassGenerator.decorateAndInject([], Stub(PropertyRoleAnnotationHandler), [], new TestCrossBuildInMemoryCacheFactory(), 0)

    def "measure class generation timing for real and realistic types"() {
        given:
        // Warm up the JVM and class generator infrastructure
        generator.generate(WarmUpBean)

        when:
        // Real Gradle types
        def results = []
        results << time("DefaultTask", DefaultTask)
        results << time("Delete", Delete)
        results << time("Exec", Exec)

        // Synthetic types that mimic real Gradle complexity
        results << time("ManyManagedProperties", ManyManagedProperties)
        results << time("DeepHierarchyLeaf", DeepHierarchyLeaf)
        results << time("ManyInheritedGenericProps", ManyInheritedGenericProps)
        results << time("LargeTaskLikeClass", LargeTaskLikeClass)
        results << time("InterfaceWithManyProps", InterfaceWithManyProps)
        results << time("MixedPropertyTypes", MixedPropertyTypes)

        then:
        // Just ensure generation succeeds - timing data is in /tmp/classgen_timing.log
        results.every { it != null }
    }

    private ClassGenerator.GeneratedClass<?> time(String label, Class<?> type) {
        long start = System.nanoTime()
        def result = generator.generate(type)
        long elapsed = System.nanoTime() - start
        println "WALLCLOCK ${label}: ${String.format('%.2f', elapsed / 1_000_000.0)}ms"
        return result
    }

    // --- Warm-up type ---
    static class WarmUpBean {
        String prop
    }

    // --- Synthetic types mimicking real Gradle complexity ---

    // Type with many managed (read-only) properties like a real task
    static abstract class ManyManagedProperties {
        abstract Property<String> getProp1()
        abstract Property<String> getProp2()
        abstract Property<String> getProp3()
        abstract Property<Integer> getProp4()
        abstract Property<Boolean> getProp5()
        abstract ListProperty<String> getItems()
        abstract SetProperty<String> getTags()
        abstract MapProperty<String, String> getMetadata()
        abstract DirectoryProperty getOutputDir()
        abstract RegularFileProperty getInputFile()
        abstract ConfigurableFileCollection getSourceFiles()
        abstract Property<String> getProp6()
        abstract Property<String> getProp7()
        abstract Property<String> getProp8()
        abstract Property<String> getProp9()
        abstract Property<String> getProp10()
        abstract ListProperty<Integer> getNumbers()
        abstract MapProperty<String, Integer> getCounts()
        abstract DirectoryProperty getBuildDir()
        abstract RegularFileProperty getManifestFile()
    }

    // Deep inheritance hierarchy
    static abstract class Level0 {
        abstract Property<String> getBase1()
        abstract Property<String> getBase2()
        String concreteField = "hello"
    }

    static abstract class Level1 extends Level0 {
        abstract Property<Integer> getMid1()
        abstract ListProperty<String> getMidList()
        abstract DirectoryProperty getMidDir()
    }

    static abstract class Level2 extends Level1 {
        abstract Property<Boolean> getDeep1()
        abstract SetProperty<String> getDeepSet()
        abstract MapProperty<String, String> getDeepMap()
    }

    static abstract class DeepHierarchyLeaf extends Level2 {
        abstract Property<String> getLeaf1()
        abstract Property<String> getLeaf2()
        abstract RegularFileProperty getLeafFile()
        abstract ConfigurableFileCollection getLeafFiles()
    }

    // Interface with generic properties inherited through hierarchy
    static interface GenericBase<T> {
        Property<T> getValue()
    }

    static interface StringGeneric extends GenericBase<String> {
        Property<String> getExtra()
    }

    static interface ExtendedStringGeneric extends StringGeneric {
        Property<String> getLabel()
    }

    static abstract class ManyInheritedGenericProps implements ExtendedStringGeneric {
        abstract Property<String> getExtra()
        abstract Property<String> getLabel()
        abstract Property<String> getValue()
        abstract Property<Integer> getCount()
        abstract ListProperty<String> getNames()
        abstract MapProperty<String, Integer> getScores()
        abstract SetProperty<String> getTags()
        abstract DirectoryProperty getOutputDir()
    }

    // Large task-like class with many properties and injected services
    static abstract class LargeTaskLikeClass extends DefaultTask {
        abstract Property<String> getMainClass()
        abstract Property<String> getModuleName()
        abstract Property<Boolean> getDebug()
        abstract Property<Integer> getMaxHeapSize()
        abstract ListProperty<String> getJvmArgs()
        abstract ListProperty<String> getArgs()
        abstract MapProperty<String, String> getEnvironment()
        abstract MapProperty<String, String> getSystemProperties()
        abstract DirectoryProperty getWorkingDir()
        abstract DirectoryProperty getOutputDirectory()
        abstract RegularFileProperty getInputFile()
        abstract RegularFileProperty getOutputFile()
        abstract ConfigurableFileCollection getClasspath()
        abstract ConfigurableFileCollection getBootstrapClasspath()
        abstract SetProperty<String> getIncludes()
        abstract SetProperty<String> getExcludes()
        abstract Property<String> getEncoding()
        abstract Property<String> getSourceCompatibility()
        abstract Property<String> getTargetCompatibility()
        abstract ListProperty<String> getCompilerArgs()
    }

    // Interface with many properties
    static interface InterfaceWithManyProps {
        Property<String> getName()
        Property<String> getDescription()
        Property<Boolean> getEnabled()
        Property<Integer> getTimeout()
        ListProperty<String> getIncludes()
        ListProperty<String> getExcludes()
        SetProperty<String> getTags()
        MapProperty<String, String> getProperties()
        DirectoryProperty getOutputDir()
        RegularFileProperty getConfigFile()
        ConfigurableFileCollection getSources()
        Property<String> getVersion()
        Property<String> getGroup()
        Property<String> getBaseName()
    }

    // Mix of concrete, abstract, and managed properties
    static abstract class MixedPropertyTypes {
        String concreteProp1 = "default1"
        String concreteProp2 = "default2"
        int concreteInt = 42
        boolean concreteBool = true

        abstract Property<String> getManagedProp1()
        abstract Property<String> getManagedProp2()
        abstract Property<Integer> getManagedProp3()
        abstract ListProperty<String> getManagedList()
        abstract MapProperty<String, String> getManagedMap()
        abstract DirectoryProperty getManagedDir()
        abstract RegularFileProperty getManagedFile()
        abstract ConfigurableFileCollection getManagedFiles()
        abstract Property<Boolean> getManagedBool()
        abstract SetProperty<String> getManagedSet()
    }
}
