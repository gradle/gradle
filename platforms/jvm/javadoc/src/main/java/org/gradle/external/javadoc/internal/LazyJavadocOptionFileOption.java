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

package org.gradle.external.javadoc.internal;

import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.FileSystemLocationProperty;
import org.gradle.api.internal.provider.EagerPropertySupport;
import org.gradle.api.provider.ListProperty;
import org.gradle.api.provider.MapProperty;
import org.gradle.api.provider.Property;
import org.gradle.external.javadoc.CoreJavadocOptions;
import org.gradle.external.javadoc.JavadocOfflineLink;
import org.gradle.internal.Cast;
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

/**
 * An option whose value lives in a lazy property of its owner.
 * Reads and writes keep the semantics of the plain option for the same kind of value,
 * and the option file gets what the plain option writes for the current value.
 *
 * <p>It keeps the owner and a non-capturing accessor instead of the property,
 * so the configuration cache restores it pointing at the owner's own lazy property.</p>
 */
@NullMarked
public final class LazyJavadocOptionFileOption<T> implements JavadocOptionFileOptionInternal<T> {

    public interface Accessor<O extends CoreJavadocOptions> extends Serializable {
        Object get(O owner);
    }

    public enum Kind {
        STRING, ENUM, BOOLEAN, FILE,
        /** Like the plain list options, setting null empties the list, so the getter never returns null. */
        MULTILINE_STRINGS,
        /** Like the plain list options, setting null empties the list, so the getter never returns null. */
        STRINGS,
        /** Like the plain path options, setting null empties the list. */
        PATH,
        /** Stores null, as the plain option does. */
        LINKS_OFFLINE,
        /** Stores null, as the plain option does. */
        GROUPS
    }

    private final String option;
    private final Kind kind;
    private final String joinBy;
    private final CoreJavadocOptions owner;
    private final Accessor<CoreJavadocOptions> accessor;

    private LazyJavadocOptionFileOption(String option, Kind kind, String joinBy, CoreJavadocOptions owner, Accessor<CoreJavadocOptions> accessor) {
        this.option = option;
        this.kind = kind;
        this.joinBy = joinBy;
        this.owner = owner;
        this.accessor = accessor;
    }

    public static <O extends CoreJavadocOptions, T> LazyJavadocOptionFileOption<T> of(String option, Kind kind, O owner, Accessor<O> accessor) {
        return of(option, kind, "", owner, accessor);
    }

    public static <O extends CoreJavadocOptions, T> LazyJavadocOptionFileOption<T> of(String option, Kind kind, String joinBy, O owner, Accessor<O> accessor) {
        return new LazyJavadocOptionFileOption<>(option, kind, joinBy, owner, Cast.uncheckedNonnullCast(accessor));
    }

    private Object property() {
        return accessor.get(owner);
    }

    @Override
    public String getOption() {
        return option;
    }

    @Override
    public @Nullable T getValue() {
        Object property = property();
        switch (kind) {
            case STRING:
            case ENUM:
            case BOOLEAN:
                return Cast.uncheckedCast(((Property<?>) property).getOrNull());
            case FILE:
                return Cast.uncheckedCast(((FileSystemLocationProperty<?>) property).getAsFile().getOrNull());
            case MULTILINE_STRINGS:
            case STRINGS:
                return Cast.uncheckedCast(EagerPropertySupport.conventionKeepingView((ListProperty<?>) property));
            case PATH:
                return Cast.uncheckedCast(new FileListView((ConfigurableFileCollection) property));
            case LINKS_OFFLINE: {
                ListProperty<?> list = (ListProperty<?>) property;
                return list.isPresent() ? Cast.uncheckedCast(EagerPropertySupport.conventionKeepingView(list)) : null;
            }
            case GROUPS: {
                MapProperty<?, ?> map = (MapProperty<?, ?>) property;
                return map.isPresent() ? Cast.uncheckedCast(EagerPropertySupport.conventionKeepingView(map)) : null;
            }
            default:
                throw new IllegalStateException(kind.name());
        }
    }

    @Override
    public void setValue(@Nullable T value) {
        Object property = property();
        switch (kind) {
            case STRING:
            case ENUM:
            case BOOLEAN:
                EagerPropertySupport.setEager(Cast.<Property<Object>>uncheckedNonnullCast(property), value);
                break;
            case FILE:
                ((FileSystemLocationProperty<?>) property).fileValue((File) value);
                break;
            case MULTILINE_STRINGS:
            case STRINGS:
                if (value == null) {
                    ((ListProperty<?>) property).empty();
                } else {
                    EagerPropertySupport.setEager(Cast.<ListProperty<Object>>uncheckedNonnullCast(property), (List<?>) value);
                }
                break;
            case PATH:
                FileListView.setFiles((ConfigurableFileCollection) property, Cast.<List<File>>uncheckedCast(value));
                break;
            case LINKS_OFFLINE:
                EagerPropertySupport.setEager(Cast.<ListProperty<Object>>uncheckedNonnullCast(property), (List<?>) value);
                break;
            case GROUPS:
                EagerPropertySupport.setEager(Cast.<MapProperty<Object, Object>>uncheckedNonnullCast(property), (Map<?, ?>) value);
                break;
            default:
                throw new IllegalStateException(kind.name());
        }
    }

    private JavadocOptionFileOptionInternal<?> plain() {
        Object value = getValue();
        switch (kind) {
            case STRING:
                return new StringJavadocOptionFileOption(option, (String) value);
            case ENUM:
                return new EnumJavadocOptionFileOption<>(option, value);
            case BOOLEAN:
                return new BooleanJavadocOptionFileOption(option, (Boolean) value);
            case FILE:
                return new FileJavadocOptionFileOption(option, (File) value);
            case MULTILINE_STRINGS:
                return new MultilineStringsJavadocOptionFileOption(option, Cast.uncheckedCast(value));
            case STRINGS:
                return new StringsJavadocOptionFileOption(option, Cast.uncheckedCast(value), joinBy);
            case PATH:
                return new PathJavadocOptionFileOption(option, Cast.uncheckedCast(value), joinBy);
            case LINKS_OFFLINE:
                return new LinksOfflineJavadocOptionFileOption(option, Cast.<List<JavadocOfflineLink>>uncheckedCast(value));
            case GROUPS:
                return new GroupsJavadocOptionFileOption(option, Cast.uncheckedCast(value));
            default:
                throw new IllegalStateException(kind.name());
        }
    }

    @Override
    public void write(JavadocOptionFileWriterContext writerContext) throws IOException {
        plain().write(writerContext);
    }

    @Override
    public JavadocOptionFileOptionInternal<T> duplicate() {
        return Cast.uncheckedNonnullCast(plain().duplicate());
    }
}
