/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.plugin.devel.tasks.internal;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.gradle.api.problems.internal.DefaultFileLocation;
import org.gradle.api.problems.internal.DefaultLineInFileLocation;
import org.gradle.api.problems.internal.DefaultOffsetInFileLocation;
import org.gradle.api.problems.internal.DefaultPluginIdLocation;
import org.gradle.api.problems.internal.DefaultProblemCategory;
import org.gradle.api.problems.internal.DefaultProblemReport;
import org.gradle.api.problems.internal.DefaultTaskPathLocation;
import org.gradle.api.problems.internal.DocLink;
import org.gradle.api.problems.internal.FileLocation;
import org.gradle.api.problems.internal.LineInFileLocation;
import org.gradle.api.problems.internal.OffsetInFileLocation;
import org.gradle.api.problems.internal.ProblemCategory;
import org.gradle.api.problems.internal.ProblemLocation;
import org.gradle.api.problems.internal.ProblemReport;
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Stream;

@Nonnull
public class ValidationProblemSerialization {
    private static final GsonBuilder GSON_BUILDER = createGsonBuilder();

    public static List<? extends ProblemReport> parseMessageList(String lines) {
        Gson gson = GSON_BUILDER.create();
        Type type = new TypeToken<List<DefaultProblemReport>>() {}.getType();
        return gson.<List<DefaultProblemReport>>fromJson(lines, type);
    }

    public static GsonBuilder createGsonBuilder() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapterFactory(new ProblemReportAdapterFactory());
        gsonBuilder.registerTypeHierarchyAdapter(DocLink.class, new DocLinkAdapter());
        gsonBuilder.registerTypeHierarchyAdapter(ProblemLocation.class, new LocationAdapter());
        gsonBuilder.registerTypeHierarchyAdapter(ProblemCategory.class, new ProblemCategoryAdapter());
        gsonBuilder.registerTypeAdapterFactory(new ThrowableAdapterFactory());

        return gsonBuilder;
    }

    public static Stream<String> toPlainMessage(List<? extends ProblemReport> problems) {
        return problems.stream()
            .map(problem -> problem.getDefinition().getSeverity() + ": " + TypeValidationProblemRenderer.renderMinimalInformationAbout(problem));
    }

    /**
     * A type adapter factory for {@link Throwable} that supports serializing and deserializing {@link Throwable} instances to JSON using GSON.
     * <p>
     * from <a href="https://github.com/eclipse-lsp4j/lsp4j/blob/main/org.eclipse.lsp4j.jsonrpc/src/main/java/org/eclipse/lsp4j/jsonrpc/json/adapters/ThrowableTypeAdapter.java">here</a>
     */
    public static class ThrowableAdapterFactory implements TypeAdapterFactory {

        @SuppressWarnings({"unchecked"})
        @Nullable
        @Override
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
            if (!Throwable.class.isAssignableFrom(typeToken.getRawType())) {
                return null;
            }

            return (TypeAdapter<T>) new ThrowableTypeAdapter((TypeToken<Throwable>) typeToken);
        }

    }

    /**
     * A type adapter for {@link Throwable} that supports serializing and deserializing {@link Throwable} instances to JSON using GSON.
     * <p>
     * from <a href="https://github.com/eclipse-lsp4j/lsp4j/blob/main/org.eclipse.lsp4j.jsonrpc/src/main/java/org/eclipse/lsp4j/jsonrpc/json/adapters/ThrowableTypeAdapter.java">here</a>
     */
    public static class ThrowableTypeAdapter extends TypeAdapter<Throwable> {
        private final TypeToken<Throwable> typeToken;

        public ThrowableTypeAdapter(TypeToken<Throwable> typeToken) {
            this.typeToken = typeToken;
        }

        @SuppressWarnings("unchecked")
        @Nullable
        @Override
        public Throwable read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return null;
            }

            in.beginObject();
            String message = null;
            Throwable cause = null;
            while (in.hasNext()) {
                String name = in.nextName();
                switch (name) {
                    case "message": {
                        message = in.nextString();
                        break;
                    }
                    case "cause": {
                        cause = read(in);
                        break;
                    }
                    default:
                        in.skipValue();
                }
            }
            in.endObject();

            try {
                Constructor<Throwable> constructor;
                if (message == null && cause == null) {
                    constructor = (Constructor<Throwable>) typeToken.getRawType().getDeclaredConstructor();
                    return constructor.newInstance();
                } else if (message == null) {
                    constructor = (Constructor<Throwable>) typeToken.getRawType()
                        .getDeclaredConstructor(Throwable.class);
                    return constructor.newInstance(cause);
                } else if (cause == null) {
                    constructor = (Constructor<Throwable>) typeToken.getRawType().getDeclaredConstructor(String.class);
                    return constructor.newInstance(message);
                } else {
                    constructor = (Constructor<Throwable>) typeToken.getRawType().getDeclaredConstructor(String.class,
                        Throwable.class);
                    return constructor.newInstance(message, cause);
                }
            } catch (NoSuchMethodException e) {
                if (message == null && cause == null) {
                    return new RuntimeException();
                } else if (message == null) {
                    return new RuntimeException(cause);
                } else if (cause == null) {
                    return new RuntimeException(message);
                } else {
                    return new RuntimeException(message, cause);
                }
            } catch (Exception e) {
                throw new JsonParseException(e);
            }
        }

        @Override
        public void write(JsonWriter out, @Nullable Throwable throwable) throws IOException {
            if (throwable == null) {
                out.nullValue();
            } else if (throwable.getMessage() == null && throwable.getCause() != null) {
                write(out, throwable.getCause());
            } else {
                out.beginObject();
                if (throwable.getMessage() != null) {
                    out.name("message");
                    out.value(throwable.getMessage());
                }
                if (shouldWriteCause(throwable)) {
                    out.name("cause");
                    write(out, throwable.getCause());
                }
                out.endObject();
            }
        }

        private static boolean shouldWriteCause(Throwable throwable) {
            Throwable cause = throwable.getCause();
            if (cause == null || cause.getMessage() == null || cause == throwable) {
                return false;
            }
            return throwable.getMessage() == null || !throwable.getMessage().contains(cause.getMessage());
        }

    }

    public static class FileLocationAdapter extends TypeAdapter<FileLocation> {

        @Override
        public void write(JsonWriter out, @Nullable FileLocation value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }

            out.beginObject();
            out.name("type").value("file");
            out.name("path").value(value.getPath());
            if (value instanceof LineInFileLocation) {
                out.name("subtype").value("lineInFile");
                LineInFileLocation l = (LineInFileLocation) value;
                out.name("line").value(l.getLine());
                out.name("column").value(l.getColumn());
                out.name("length").value(l.getLength());
            } else if (value instanceof OffsetInFileLocation) {
                out.name("subtype").value("offsetInFile");
                OffsetInFileLocation l = (OffsetInFileLocation) value;
                out.name("offset").value(l.getOffset());
                out.name("length").value(l.getLength());
            } else {
                out.name("subtype").value("file");
            }
            out.endObject();
        }

        @Override
        public FileLocation read(JsonReader in) throws IOException {
            in.beginObject();
            FileLocation fileLocation = readObject(in);
            in.endObject();

            Objects.requireNonNull(fileLocation, "path must not be null");
            return fileLocation;
        }

        @Nonnull
        private static FileLocation readObject(JsonReader in) throws IOException {
            String subtype = null;
            String path = null;
            Integer offset = null;
            Integer line = null;
            Integer column = null;
            Integer length = null;
            while (in.hasNext()) {
                String name = in.nextName();
                switch (name) {
                    case "subtype": {
                        subtype = in.nextString();
                        break;
                    }
                    case "path": {
                        path = in.nextString();
                        break;
                    }
                    case "offset": {
                        offset = in.nextInt();
                        break;
                    }
                    case "line": {
                        line = in.nextInt();
                        break;
                    }
                    case "column": {
                        column = in.nextInt();
                        break;
                    }
                    case "length": {
                        length = in.nextInt();
                        break;
                    }
                    default:
                        in.skipValue();
                }
            }

            if (subtype.equals("lineInFile")) {
                return DefaultLineInFileLocation.from(path, line, column, length);
            } else if (subtype.equals("offsetInFile")) {
                return DefaultOffsetInFileLocation.from(path, offset, length);
            } else {
                return DefaultFileLocation.from(path);
            }
        }
    }

    public static class PluginIdLocationAdapter extends TypeAdapter<DefaultPluginIdLocation> {

        @Override
        public void write(JsonWriter out, @Nullable DefaultPluginIdLocation value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }

            out.beginArray();
            out.name("type").value("pluginId");
            out.name("pluginId").value(value.getPluginId());
            out.endObject();
        }

        @Override
        public DefaultPluginIdLocation read(JsonReader in) throws IOException {
            in.beginObject();
            DefaultPluginIdLocation problemLocation = readObject(in);
            in.endObject();

            Objects.requireNonNull(problemLocation, "pluginId must not be null");
            return problemLocation;
        }

        private static DefaultPluginIdLocation readObject(JsonReader in) throws IOException {
            String pluginId = null;
            while (in.hasNext()) {
                String name = in.nextName();
                if (name.equals("pluginId")) {
                    pluginId = in.nextString();
                } else {
                    in.skipValue();
                }
            }
            return new DefaultPluginIdLocation(pluginId);
        }
    }

    public static class TaskLocationAdapter extends TypeAdapter<DefaultTaskPathLocation> {

        @Override
        public void write(JsonWriter out, @Nullable DefaultTaskPathLocation value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }

            out.beginArray();
            out.name("type").value("task");
            out.name("buildTreePath").value(value.getBuildTreePath());
            out.endObject();
        }

        @Override
        public DefaultTaskPathLocation read(JsonReader in) throws IOException {
            in.beginObject();
            DefaultTaskPathLocation buildTreePath = readObject(in);
            in.endObject();

            Objects.requireNonNull(buildTreePath, "buildTreePath must not be null");
            return buildTreePath;
        }

        @Nonnull
        private static DefaultTaskPathLocation readObject(JsonReader in) throws IOException {
            String buildTreePath = null;
            while (in.hasNext()) {
                String name = in.nextName();
                if (name.equals("buildTreePath")) {
                    buildTreePath = in.nextString();
                } else {
                    in.skipValue();
                }
            }
            return new DefaultTaskPathLocation(buildTreePath);
        }
    }

    public static class DocLinkAdapter extends TypeAdapter<DocLink> {

        @Override
        public void write(JsonWriter out, @Nullable DocLink value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }

            out.beginObject();
            out.name("url").value(value.getUrl());
            out.name("consultDocumentationMessage").value(value.getConsultDocumentationMessage());
            out.endObject();
        }

        @Override
        public DocLink read(JsonReader in) throws IOException {
            in.beginObject();
            String url = null;
            String consultDocumentationMessage = null;
            while (in.hasNext()) {
                String name = in.nextName();
                switch (name) {
                    case "url": {
                        url = in.nextString();
                        break;
                    }
                    case "consultDocumentationMessage": {
                        consultDocumentationMessage = in.nextString();
                        break;
                    }
                    default:
                        in.skipValue();
                }
            }
            in.endObject();

            final String finalUrl = url;
            final String finalConsultDocumentationMessage = consultDocumentationMessage;
            return new DocLink() {
                @Override
                public String getUrl() {
                    return finalUrl;
                }

                @Override
                public String getConsultDocumentationMessage() {
                    return finalConsultDocumentationMessage;
                }
            };
        }
    }

    public static class ProblemCategoryAdapter extends TypeAdapter<ProblemCategory> {

        @Override
        public void write(JsonWriter out, @Nullable ProblemCategory value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }

            out.beginObject();
            out.name("namespace").value(value.getNamespace());
            out.name("category").value(value.getCategory());
            out.name("subcategories").beginArray();
            for (String sc : value.getSubcategories()) {
                out.value(sc);
            }
            out.endArray();
            out.endObject();
        }

        @Override
        public ProblemCategory read(JsonReader in) throws IOException {
            in.beginObject();
            String namespace = null;
            String category = null;
            List<String> subcategories = new ArrayList<>();
            String name;
            while (in.hasNext()) {
                name = in.nextName();
                switch (name) {
                    case "namespace": {
                        namespace = in.nextString();
                        break;
                    }
                    case "category": {
                        category = in.nextString();
                        break;
                    }
                    case "subcategories": {
                        in.beginArray();
                        while (in.hasNext()) {
                            subcategories.add(in.nextString());
                        }
                        in.endArray();
                        break;
                    }
                    default:
                        in.skipValue();
                }
            }
            in.endObject();
            return DefaultProblemCategory.create(namespace, category, subcategories.toArray(new String[0]));
        }
    }

    private static class LocationAdapter extends TypeAdapter<ProblemLocation> {
        @Override
        public void write(JsonWriter out, ProblemLocation value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }

            if (value instanceof DefaultFileLocation) {
                new FileLocationAdapter().write(out, (DefaultFileLocation) value);
                return;
            }
            if (value instanceof DefaultPluginIdLocation) {
                new PluginIdLocationAdapter().write(out, (DefaultPluginIdLocation) value);
                return;
            }
            if (value instanceof DefaultTaskPathLocation) {
                new TaskLocationAdapter().write(out, (DefaultTaskPathLocation) value);
            }
        }

        @Override
        public ProblemLocation read(JsonReader in) throws IOException {
            if (in.hasNext()) {
                in.beginObject();
                try {
                    String type = null;
                    String name = in.nextName();
                    if (name.equals("type")) {
                        type = in.nextString();
                    }
                    if (type == null) {
                        throw new JsonParseException("type must not be null");
                    }

                    switch (type) {
                        case "file":
                            return FileLocationAdapter.readObject(in);
                        case "pluginId":
                            return PluginIdLocationAdapter.readObject(in);
                        case "task":
                            return TaskLocationAdapter.readObject(in);
                        default:
                            throw new JsonParseException("Unknown type: " + type);
                    }
                } finally {
                    in.endObject();
                }
            }
            return null;
        }
    }
}
