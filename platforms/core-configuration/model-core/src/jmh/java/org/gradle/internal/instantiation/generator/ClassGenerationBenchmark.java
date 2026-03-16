/*
 * Copyright 2025 the original author or authors.
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
package org.gradle.internal.instantiation.generator;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.Named;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.provider.SetProperty;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFiles;
import org.gradle.api.tasks.Nested;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.OutputFile;
import org.gradle.cache.Cache;
import org.gradle.cache.internal.ClassCacheFactory;
import org.gradle.cache.internal.MapBackedCache;
import org.gradle.internal.instantiation.PropertyRoleAnnotationHandler;
import org.gradle.internal.state.ModelObject;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks for measuring the one-time cost of generating decorated/injected subclasses.
 *
 * <p>Measures the full generation pipeline: reflection-based class inspection,
 * ASM bytecode emission (with COMPUTE_FRAMES), and JVM class definition.
 *
 * <p>Uses {@link Mode#SingleShotTime} since class generation is a one-time cost per class.
 * Each fork starts with a fresh JVM and empty caches, measuring true cold generation.
 * JIT is warmed up by generating throwaway types in setup before measuring target types.
 *
 * <p>Run with: {@code ./gradlew :model-core:jmh -Pjmh.include=ClassGenerationBenchmark}
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.SingleShotTime)
@OutputTimeUnit(TimeUnit.MICROSECONDS)
@Warmup(iterations = 0)
@Measurement(iterations = 1)
@Fork(25)
public class ClassGenerationBenchmark {

    private ClassGenerator decoratedGenerator;
    private ClassGenerator injectOnlyGenerator;

    @Setup(Level.Trial)
    public void setup() {
        ClassCacheFactory cacheFactory = new BenchmarkClassCacheFactory();
        PropertyRoleAnnotationHandler roleHandler = new NoOpPropertyRoleAnnotationHandler();

        injectOnlyGenerator = AsmBackedClassGenerator.injectOnly(
            Collections.emptyList(), roleHandler, ImmutableSet.of(), cacheFactory, 0);

        // Use a non-empty enabledAnnotations set to force decorateAndInject to create
        // its own private cache instead of using the shared static GENERATED_CLASSES_CACHES
        decoratedGenerator = AsmBackedClassGenerator.decorateAndInject(
            Collections.emptyList(), roleHandler, ImmutableSet.of(BenchmarkMarker.class), cacheFactory, 0);

        // Warm up JIT by generating throwaway types that exercise the same code paths
        decoratedGenerator.generate(JitWarmup1.class);
        decoratedGenerator.generate(JitWarmup2.class);
        decoratedGenerator.generate(JitWarmup3.class);
        injectOnlyGenerator.generate(JitWarmup1.class);
        injectOnlyGenerator.generate(JitWarmup2.class);
        injectOnlyGenerator.generate(JitWarmup3.class);
    }

    // --- Decorated generation benchmarks ---

    @Benchmark
    public Object generateDecoratedPlainClass() {
        return decoratedGenerator.generate(PlainObject.class);
    }

    @Benchmark
    public Object generateDecoratedClassWithConstructorArg() {
        return decoratedGenerator.generate(ObjectWithConstructorArg.class);
    }

    @Benchmark
    public Object generateDecoratedInterfaceWithManagedProperties() {
        return decoratedGenerator.generate(InterfaceWithManagedProperties.class);
    }

    @Benchmark
    public Object generateDecoratedClassWithInjection() {
        return decoratedGenerator.generate(ClassWithInjectedServices.class);
    }

    @Benchmark
    public Object generateDecoratedInterfaceWithPlainProperties() {
        return decoratedGenerator.generate(InterfaceWithPlainProperties.class);
    }

    @Benchmark
    public Object generateDecoratedClassWithPlainProperties() {
        return decoratedGenerator.generate(ClassWithPlainProperties.class);
    }

    // --- Inject-only generation benchmarks ---

    @Benchmark
    public Object generateInjectOnlyPlainClass() {
        return injectOnlyGenerator.generate(PlainObject.class);
    }

    @Benchmark
    public Object generateInjectOnlyClassWithConstructorArg() {
        return injectOnlyGenerator.generate(ObjectWithConstructorArg.class);
    }

    @Benchmark
    public Object generateInjectOnlyInterfaceWithManagedProperties() {
        return injectOnlyGenerator.generate(InterfaceWithManagedProperties.class);
    }

    @Benchmark
    public Object generateInjectOnlyInterfaceWithPlainProperties() {
        return injectOnlyGenerator.generate(InterfaceWithPlainProperties.class);
    }

    @Benchmark
    public Object generateInjectOnlyClassWithPlainProperties() {
        return injectOnlyGenerator.generate(ClassWithPlainProperties.class);
    }

    @Benchmark
    public Object generateInjectOnlyClassWithInjection() {
        return injectOnlyGenerator.generate(ClassWithInjectedServices.class);
    }

    // --- JIT warmup types (distinct from benchmark target types) ---

    public static class JitWarmup1 {
        public JitWarmup1() {}
    }

    public static class JitWarmup2 {
        private final String value;

        @Inject
        public JitWarmup2(String value) {
            this.value = value;
        }

        public String getValue() {
            return value;
        }
    }

    public interface JitWarmup3 extends Named {
        Property<String> getFoo();
    }

    // --- Benchmark target types ---

    /**
     * Minimal class — measures baseline generation cost (infrastructure methods only).
     */
    public static class PlainObject {
        public PlainObject() {}
    }

    /**
     * Class with a constructor argument — adds constructor wrapping overhead.
     */
    public static class ObjectWithConstructorArg {
        private final String name;

        @Inject
        public ObjectWithConstructorArg(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public interface NestedBean {
        Property<String> getBeanValue();
    }

    /**
     * Interface with many managed properties — the most expensive generation case.
     * Exercises managed state, factory support, convention mapping, lazy getters,
     * LazyGroovySupport overloads, and nested object creation.
     */
    public interface InterfaceWithManagedProperties extends Named {
        @InputFiles
        ConfigurableFileCollection getInputFiles();

        @OutputFile
        RegularFileProperty getOutputFile();

        @OutputDirectory
        DirectoryProperty getOutputDirectory();

        @Input
        Property<Boolean> getVerbose();

        @Input
        ListProperty<String> getMessages();

        @Input
        SetProperty<Integer> getFavoriteNumbers();

        @Input
        MapProperty<String, String> getMetadata();

        @Nested
        NestedBean getNestedBean();
    }

    /**
     * Interface with the same number of properties as InterfaceWithManagedProperties,
     * but using plain getters/setters instead of Provider API types.
     * This isolates the cost of managed property generation from Provider property generation.
     */
    public interface InterfaceWithPlainProperties extends Named {
        @Input
        String getLabel();
        void setLabel(String label);

        @Input
        Boolean getVerbose();
        void setVerbose(Boolean verbose);

        @Input
        Integer getCount();
        void setCount(Integer count);

        @Input
        String getDescription();
        void setDescription(String description);

        @Input
        String getCategory();
        void setCategory(String category);

        @Input
        String getVersion();
        void setVersion(String version);

        @Input
        Boolean getEnabled();
        void setEnabled(Boolean enabled);

        @Nested
        NestedBean getNestedBean();
    }

    /**
     * Abstract class with manually implemented plain properties.
     * The generator does not need to synthesize getters/setters — only infrastructure methods.
     */
    public static abstract class ClassWithPlainProperties implements Named {
        private String name;
        private String label;
        private Boolean verbose;
        private Integer count;
        private String description;
        private String category;
        private String version;
        private Boolean enabled;

        @Inject
        public ClassWithPlainProperties(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Input
        public String getLabel() {
            return label;
        }

        public void setLabel(String label) {
            this.label = label;
        }

        @Input
        public Boolean getVerbose() {
            return verbose;
        }

        public void setVerbose(Boolean verbose) {
            this.verbose = verbose;
        }

        @Input
        public Integer getCount() {
            return count;
        }

        public void setCount(Integer count) {
            this.count = count;
        }

        @Input
        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        @Input
        public String getCategory() {
            return category;
        }

        public void setCategory(String category) {
            this.category = category;
        }

        @Input
        public String getVersion() {
            return version;
        }

        public void setVersion(String version) {
            this.version = version;
        }

        @Input
        public Boolean getEnabled() {
            return enabled;
        }

        public void setEnabled(Boolean enabled) {
            this.enabled = enabled;
        }
    }

    public interface ServiceA {}

    public interface ServiceB {}

    /**
     * Abstract class with {@code @Inject} service properties — exercises the
     * service injection code path (lazy field + service lookup getter).
     */
    public static abstract class ClassWithInjectedServices {
        @Inject
        protected abstract ServiceA getServiceA();

        @Inject
        protected abstract ServiceB getServiceB();

        public ClassWithInjectedServices() {}
    }

    // --- Infrastructure ---

    /**
     * Dummy annotation used to force {@code decorateAndInject} to create its own
     * private cache instead of the shared static one.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @interface BenchmarkMarker {}

    private static class BenchmarkClassCacheFactory implements ClassCacheFactory {
        @Override
        public <V> Cache<Class<?>, V> newClassCache() {
            return new MapBackedCache<>(new ConcurrentHashMap<>());
        }

        @Override
        public <V> Cache<Class<?>, V> newClassMap() {
            return new MapBackedCache<>(new ConcurrentHashMap<>());
        }
    }

    private static class NoOpPropertyRoleAnnotationHandler implements PropertyRoleAnnotationHandler {
        @Override
        public Set<Class<? extends Annotation>> getAnnotationTypes() {
            return Collections.emptySet();
        }

        @Override
        public void applyRoleTo(ModelObject owner, Object target) {
        }
    }
}
