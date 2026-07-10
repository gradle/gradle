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
import org.gradle.internal.instantiation.InstanceGenerator;
import org.gradle.internal.instantiation.InstantiatorFactory;
import org.gradle.internal.instantiation.PropertyRoleAnnotationHandler;
import org.gradle.internal.service.ServiceLookup;
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
import org.openjdk.jmh.infra.Blackhole;

import javax.inject.Inject;
import java.lang.annotation.Annotation;
import java.lang.reflect.Type;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Benchmarks for object instantiation and decoration performance.
 *
 * Run with: ./gradlew :model-core:jmh
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.Throughput)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(2)
public class InstantiationBenchmark {

    private InstanceGenerator decoratedInstantiator;
    private InstanceGenerator injectOnlyInstantiator;

    @Setup(Level.Trial)
    public void setup() {
        ClassCacheFactory cacheFactory = new BenchmarkClassCacheFactory();
        PropertyRoleAnnotationHandler roleHandler = new NoOpPropertyRoleAnnotationHandler();
        InstantiatorFactory factory = new DefaultInstantiatorFactory(cacheFactory, Collections.emptyList(), roleHandler);

        ServiceLookup services = new NoOpServiceLookup();
        decoratedInstantiator = factory.decorate(services);
        injectOnlyInstantiator = factory.inject(services);
    }

    // --- Decorated instantiation benchmarks ---

    @Benchmark
    public void decoratePlainObject(Blackhole bh) {
        bh.consume(decoratedInstantiator.newInstance(PlainObject.class));
    }

    @Benchmark
    public void decorateObjectWithConstructorArg(Blackhole bh) {
        bh.consume(decoratedInstantiator.newInstance(ObjectWithConstructorArg.class, "hello"));
    }

    @Benchmark
    public void decorateObjectWithProperties(Blackhole bh) {
        bh.consume(decoratedInstantiator.newInstance(ObjectWithProperties.class, "hello"));
    }


    @Benchmark
    public void decorateObjectWithPlainProperties(Blackhole bh) {
        bh.consume(decoratedInstantiator.newInstance(ObjectWithPlainProperties.class, "hello"));
    }

    @Benchmark
    public void decorateObjectWithImplementedPlainProperties(Blackhole bh) {
        bh.consume(decoratedInstantiator.newInstance(ObjectWithImplementedPlainProperties.class, "hello"));
    }

    @Benchmark
    public void decorateMultipleConstructorArgs(Blackhole bh) {
        bh.consume(decoratedInstantiator.newInstance(ObjectWithMultipleArgs.class, "hello", 42));
    }

    // --- Inject-only instantiation benchmarks ---

    @Benchmark
    public void injectOnlyPlainObject(Blackhole bh) {
        bh.consume(injectOnlyInstantiator.newInstance(PlainObject.class));
    }

    @Benchmark
    public void injectOnlyObjectWithConstructorArg(Blackhole bh) {
        bh.consume(injectOnlyInstantiator.newInstance(ObjectWithConstructorArg.class, "hello"));
    }

    // --- Direct constructor call baselines ---

    @Benchmark
    public void directPlainObject(Blackhole bh) {
        bh.consume(new PlainObject());
    }

    @Benchmark
    public void directObjectWithConstructorArg(Blackhole bh) {
        bh.consume(new ObjectWithConstructorArg("hello"));
    }

    @Benchmark
    public void directMultipleConstructorArgs(Blackhole bh) {
        bh.consume(new ObjectWithMultipleArgs("hello", 42));
    }

    // --- Test types ---

    public static class PlainObject {
        public PlainObject() {}
    }

    public interface NestedBean {
        Property<String> getBeanMessage();
    }
    public interface ObjectWithProperties extends Named {
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
        MapProperty<String, String> getNickNames();

        @Nested
        NestedBean getNestedBean();
    }

    public interface NestedPlainBean {
        String getBeanValue();
        void setBeanValue(String value);
    }

    /**
     * Interface with plain getter/setter properties instead of Provider API types.
     * Same number of properties as ObjectWithProperties for comparison.
     */
    public interface ObjectWithPlainProperties extends Named {
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
        NestedPlainBean getNestedBean();
    }

    /**
     * Abstract class with manually implemented plain properties.
     * The generator does not need to synthesize getters/setters.
     */
    public static abstract class ObjectWithImplementedPlainProperties implements Named {
        private String name;
        private String label;
        private Boolean verbose;
        private Integer count;
        private String description;
        private String category;
        private String version;
        private Boolean enabled;

        @Inject
        public ObjectWithImplementedPlainProperties(String name) {
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

    public static class ObjectWithMultipleArgs {
        private final String name;
        private final int count;

        @Inject
        public ObjectWithMultipleArgs(String name, int count) {
            this.name = name;
            this.count = count;
        }

        public String getName() {
            return name;
        }

        public int getCount() {
            return count;
        }
    }

    // --- Minimal infrastructure ---

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

    private static class NoOpServiceLookup implements ServiceLookup {
        @Override
        public Object find(Type serviceType) {
            return null;
        }

        @Override
        public Object get(Type serviceType) throws IllegalArgumentException {
            throw new IllegalArgumentException("No service of type " + serviceType);
        }

        @Override
        public Object get(Type serviceType, Class<? extends Annotation> annotatedWith) throws IllegalArgumentException {
            throw new IllegalArgumentException("No service of type " + serviceType);
        }
    }
}
