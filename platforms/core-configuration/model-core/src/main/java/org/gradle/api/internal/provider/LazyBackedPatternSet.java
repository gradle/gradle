/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.provider;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import org.gradle.api.Transformer;
import org.gradle.api.file.FileTreeElement;
import org.gradle.api.internal.tasks.TaskDependencyContainer;
import org.gradle.api.internal.tasks.TaskDependencyResolveContext;
import org.gradle.api.provider.Property;
import org.gradle.api.specs.Spec;
import org.gradle.api.tasks.util.FilePatterns;
import org.gradle.api.tasks.util.PatternSet;
import org.gradle.api.tasks.util.internal.DefaultFilePatterns;
import org.gradle.internal.Cast;
import org.gradle.internal.typeconversion.NotationParser;
import org.gradle.internal.typeconversion.NotationParserBuilder;

import java.util.AbstractSet;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * A {@link PatternSet} whose patterns live in a {@code Property<FilePatterns>}.
 *
 * <p>Each eager change is a read-modify-write of the property. When the property holds a provider that is
 * not a fixed value, the change is applied lazily on top of it, so an eager call never evaluates that provider.
 * A change on top of the convention keeps the convention's content.</p>
 */
public class LazyBackedPatternSet extends PatternSet implements TaskDependencyContainer {

    private static final NotationParser<Object, String> PARSER = NotationParserBuilder.toType(String.class).fromCharSequence().toComposite();

    private final DefaultProperty<FilePatterns> property;

    /**
     * @param template a pattern set from {@code PatternSetFactory}, whose spec factory this set uses
     */
    public LazyBackedPatternSet(PatternSet template, Property<FilePatterns> property) {
        super(template);
        this.property = Cast.uncheckedNonnullCast(property);
    }

    public Property<FilePatterns> getProperty() {
        return property;
    }

    public FilePatterns getValue() {
        return property.getOrElse(FilePatterns.empty());
    }

    /**
     * Whether the property holds a value that is only known by evaluating a provider.
     */
    public boolean isBoundLazily() {
        Object current = property.getProvider();
        return !(current instanceof Providers.FixedValueProvider) && current != Providers.notDefined();
    }

    private void update(Transformer<FilePatterns, FilePatterns> change) {
        if (isBoundLazily()) {
            // No orElse(empty) here: visiting the dependencies of an orElse provider calculates its left side.
            // So a change on top of a binding without a value is lost.
            property.replace(old -> old.map(change));
        } else {
            property.set(change.transform(getValue()));
        }
    }

    @Override
    public void visitDependencies(TaskDependencyResolveContext context) {
        property.visitDependencies(context);
    }

    @Override
    public Set<String> getIncludesView() {
        return getValue().getIncludes();
    }

    @Override
    public Set<String> getExcludesView() {
        return getValue().getExcludes();
    }

    @Override
    public Set<Spec<FileTreeElement>> getIncludeSpecsView() {
        return getValue().getIncludeSpecs();
    }

    @Override
    public Set<Spec<FileTreeElement>> getExcludeSpecsView() {
        return getValue().getExcludeSpecs();
    }

    @Override
    public Set<String> getIncludes() {
        return new PartView<>(Part.INCLUDES);
    }

    @Override
    public Set<String> getExcludes() {
        return new PartView<>(Part.EXCLUDES);
    }

    @Override
    public Set<Spec<FileTreeElement>> getIncludeSpecs() {
        return new PartView<>(Part.INCLUDE_SPECS);
    }

    @Override
    public Set<Spec<FileTreeElement>> getExcludeSpecs() {
        return new PartView<>(Part.EXCLUDE_SPECS);
    }

    @Override
    public boolean isCaseSensitive() {
        return getValue().isCaseSensitive();
    }

    @Override
    public void setCaseSensitive(boolean caseSensitive) {
        update(new SetCaseSensitive(caseSensitive));
    }

    @Override
    public PatternSet setIncludes(Iterable<String> includes) {
        setPart(Part.INCLUDES, includes);
        return this;
    }

    @Override
    public PatternSet setExcludes(Iterable<String> excludes) {
        setPart(Part.EXCLUDES, excludes);
        return this;
    }

    private void setPart(Part part, Iterable<?> values) {
        if (values instanceof PartView && ((PartView<?>) values).isViewOf(this, part)) {
            // Setting the patterns to their own view changes nothing, and must not evaluate a lazy value
            return;
        }
        update(new Replace(part, part.parse(values)));
    }

    @Override
    public PatternSet include(String... includes) {
        update(new Add(Part.INCLUDES, ImmutableList.copyOf(includes)));
        return this;
    }

    @Override
    public PatternSet include(Iterable includes) {
        update(new Add(Part.INCLUDES, Part.INCLUDES.parse(Cast.<Iterable<?>>uncheckedNonnullCast(includes))));
        return this;
    }

    @Override
    public PatternSet include(Spec<FileTreeElement> spec) {
        update(new Add(Part.INCLUDE_SPECS, ImmutableList.of(spec)));
        return this;
    }

    @Override
    public PatternSet includeSpecs(Iterable<Spec<FileTreeElement>> includeSpecs) {
        update(new Add(Part.INCLUDE_SPECS, ImmutableList.copyOf(includeSpecs)));
        return this;
    }

    @Override
    public PatternSet exclude(String... excludes) {
        update(new Add(Part.EXCLUDES, ImmutableList.copyOf(excludes)));
        return this;
    }

    @Override
    public PatternSet exclude(Iterable excludes) {
        update(new Add(Part.EXCLUDES, Part.EXCLUDES.parse(Cast.<Iterable<?>>uncheckedNonnullCast(excludes))));
        return this;
    }

    @Override
    public PatternSet exclude(Spec<FileTreeElement> spec) {
        update(new Add(Part.EXCLUDE_SPECS, ImmutableList.of(spec)));
        return this;
    }

    @Override
    public PatternSet excludeSpecs(Iterable<Spec<FileTreeElement>> excludes) {
        update(new Add(Part.EXCLUDE_SPECS, ImmutableList.copyOf(excludes)));
        return this;
    }

    @Override
    protected PatternSet doCopyFrom(PatternSet from) {
        // copyFrom replaces everything, as on a plain pattern set, so it drops a lazy binding
        PatternSet copy = new PatternSet().copyFrom(from);
        property.set(new DefaultFilePatterns(
            copy.getIncludesView(), copy.getExcludesView(), copy.getIncludeSpecsView(), copy.getExcludeSpecsView(), copy.isCaseSensitive()
        ));
        return this;
    }

    private enum Part {
        INCLUDES {
            @Override
            Set<?> get(FilePatterns patterns) {
                return patterns.getIncludes();
            }

            @Override
            FilePatterns with(FilePatterns patterns, Iterable<?> values) {
                return patterns.withIncludes(Cast.<Iterable<String>>uncheckedNonnullCast(values));
            }
        },
        EXCLUDES {
            @Override
            Set<?> get(FilePatterns patterns) {
                return patterns.getExcludes();
            }

            @Override
            FilePatterns with(FilePatterns patterns, Iterable<?> values) {
                return patterns.withExcludes(Cast.<Iterable<String>>uncheckedNonnullCast(values));
            }
        },
        INCLUDE_SPECS {
            @Override
            Set<?> get(FilePatterns patterns) {
                return patterns.getIncludeSpecs();
            }

            @Override
            FilePatterns with(FilePatterns patterns, Iterable<?> values) {
                return patterns.withIncludeSpecs(Cast.<Iterable<Spec<FileTreeElement>>>uncheckedNonnullCast(values));
            }
        },
        EXCLUDE_SPECS {
            @Override
            Set<?> get(FilePatterns patterns) {
                return patterns.getExcludeSpecs();
            }

            @Override
            FilePatterns with(FilePatterns patterns, Iterable<?> values) {
                return patterns.withExcludeSpecs(Cast.<Iterable<Spec<FileTreeElement>>>uncheckedNonnullCast(values));
            }
        };

        abstract Set<?> get(FilePatterns patterns);

        abstract FilePatterns with(FilePatterns patterns, Iterable<?> values);

        boolean isStrings() {
            return this == INCLUDES || this == EXCLUDES;
        }

        ImmutableList<Object> parse(Iterable<?> values) {
            ImmutableList.Builder<Object> parsed = ImmutableList.builder();
            for (Object value : values) {
                parsed.add(isStrings() ? PARSER.parseNotation(value) : value);
            }
            return parsed.build();
        }
    }

    // The changes are classes, not lambdas, so a lazily applied change can be stored in the configuration cache

    private static final class Add implements Transformer<FilePatterns, FilePatterns> {
        private final Part part;
        private final ImmutableList<Object> values;

        Add(Part part, ImmutableList<Object> values) {
            this.part = part;
            this.values = values;
        }

        @Override
        public FilePatterns transform(FilePatterns patterns) {
            Set<Object> result = new LinkedHashSet<>(part.get(patterns));
            result.addAll(values);
            return part.with(patterns, result);
        }
    }

    private static final class Remove implements Transformer<FilePatterns, FilePatterns> {
        private final Part part;
        private final ImmutableList<Object> values;
        private final boolean retain;

        Remove(Part part, ImmutableList<Object> values, boolean retain) {
            this.part = part;
            this.values = values;
            this.retain = retain;
        }

        @Override
        public FilePatterns transform(FilePatterns patterns) {
            Set<Object> result = new LinkedHashSet<>(part.get(patterns));
            if (retain) {
                result.retainAll(values);
            } else {
                result.removeAll(values);
            }
            return part.with(patterns, result);
        }
    }

    private static final class Replace implements Transformer<FilePatterns, FilePatterns> {
        private final Part part;
        private final ImmutableList<Object> values;

        Replace(Part part, ImmutableList<Object> values) {
            this.part = part;
            this.values = values;
        }

        @Override
        public FilePatterns transform(FilePatterns patterns) {
            return part.with(patterns, values);
        }
    }

    private static final class SetCaseSensitive implements Transformer<FilePatterns, FilePatterns> {
        private final boolean caseSensitive;

        SetCaseSensitive(boolean caseSensitive) {
            this.caseSensitive = caseSensitive;
        }

        @Override
        public FilePatterns transform(FilePatterns patterns) {
            return patterns.caseSensitive(caseSensitive);
        }
    }

    /**
     * A write-through view of one part of the patterns.
     *
     * <p>When the property is bound lazily, {@code add} and {@code remove} return {@code true} without
     * checking the current value, so that they do not evaluate the binding.</p>
     */
    private class PartView<E> extends AbstractSet<E> {
        private final Part part;

        PartView(Part part) {
            this.part = part;
        }

        boolean isViewOf(LazyBackedPatternSet owner, Part part) {
            return owner == LazyBackedPatternSet.this && this.part == part;
        }

        private Set<E> current() {
            return Cast.uncheckedNonnullCast(part.get(getValue()));
        }

        @Override
        public Iterator<E> iterator() {
            Iterator<E> iterator = ImmutableSet.copyOf(current()).iterator();
            return new Iterator<E>() {
                private E last;

                @Override
                public boolean hasNext() {
                    return iterator.hasNext();
                }

                @Override
                public E next() {
                    last = iterator.next();
                    return last;
                }

                @Override
                public void remove() {
                    PartView.this.remove(last);
                }
            };
        }

        @Override
        public int size() {
            return current().size();
        }

        @Override
        public boolean contains(Object o) {
            return current().contains(o);
        }

        @Override
        public boolean add(E e) {
            ImmutableList<Object> values = part.parse(ImmutableList.of(e));
            boolean lazy = isBoundLazily();
            boolean added = lazy || !current().contains(values.get(0));
            update(new Add(part, values));
            return added;
        }

        @Override
        public boolean addAll(Collection<? extends E> c) {
            ImmutableList<Object> values = part.parse(c);
            boolean lazy = isBoundLazily();
            boolean added = lazy || !current().containsAll(values);
            update(new Add(part, values));
            return added;
        }

        @Override
        public boolean remove(Object o) {
            return removeOrRetain(ImmutableList.of(o), false);
        }

        @Override
        public boolean removeAll(Collection<?> c) {
            return removeOrRetain(ImmutableList.copyOf(c), false);
        }

        @Override
        public boolean retainAll(Collection<?> c) {
            return removeOrRetain(ImmutableList.copyOf(c), true);
        }

        private boolean removeOrRetain(ImmutableList<Object> values, boolean retain) {
            boolean changed;
            if (isBoundLazily()) {
                changed = true;
            } else {
                Set<Object> before = new LinkedHashSet<>(current());
                changed = retain ? before.retainAll(values) : before.removeAll(values);
            }
            update(new Remove(part, values, retain));
            return changed;
        }

        @Override
        public void clear() {
            update(new Replace(part, ImmutableList.of()));
        }

        @Override
        public String toString() {
            return current().toString();
        }

        @Override
        public Object[] toArray() {
            return current().toArray();
        }

        @Override
        public <T> T[] toArray(T[] a) {
            return current().toArray(a);
        }
    }
}
