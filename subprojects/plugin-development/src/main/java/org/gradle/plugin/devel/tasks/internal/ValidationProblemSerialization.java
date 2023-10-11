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
import org.gradle.api.problems.DocLink;
import org.gradle.api.problems.ReportableProblem;
import org.gradle.api.problems.internal.DefaultReportableProblem;
import org.gradle.api.problems.internal.InternalProblems;
import org.gradle.api.problems.locations.ProblemLocation;
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.List;
import java.util.stream.Stream;

@Nonnull
public class ValidationProblemSerialization {
    public static List<? extends ReportableProblem> parseMessageList(String lines, InternalProblems problemService) {
        GsonBuilder gsonBuilder = createGsonBuilder();
        Gson gson = gsonBuilder.create();
        Type type = new TypeToken<List<DefaultReportableProblem>>() {}.getType();
        List<DefaultReportableProblem> reportableProblems = gson.fromJson(lines, type);
        reportableProblems.forEach(problem -> problem.setProblemService(problemService));
        return reportableProblems;
    }

    public static GsonBuilder createGsonBuilder() {
        GsonBuilder gsonBuilder = new GsonBuilder();

        gsonBuilder.registerTypeHierarchyAdapter(DocLink.class, new DocLinkAdapter());
        gsonBuilder.registerTypeHierarchyAdapter(ProblemLocation.class, new LocationAdapter());
        gsonBuilder.registerTypeAdapterFactory(new ThrowableAdapterFactory());

        return gsonBuilder;
    }

    public static Stream<String> toPlainMessage(List<? extends ReportableProblem> problems) {
        return problems.stream()
            .map(problem -> problem.getSeverity() + ": " + TypeValidationProblemRenderer.renderMinimalInformationAbout(problem));
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

    private static class LocationAdapter extends TypeAdapter<ProblemLocation> {
        @Override
        public void write(JsonWriter out, ProblemLocation value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }

            out.beginObject();
            out.name("type").value(value.getClass().getName());
            // Serialize the rest of the object by serializing all the fields
            Gson gson = new Gson();
            gson.toJson(value, value.getClass(), out);
            out.endObject();
        }

        @Override
        public ProblemLocation read(JsonReader in) throws IOException {
            in.beginObject();
            String type = null;
            while (in.hasNext()) {
                String name = in.nextName();
                if (name.equals("type")) {
                    type = in.nextString();
                } else {
                    in.skipValue();
                }
            }
            in.endObject();

            if (type == null) {
                throw new JsonParseException("Missing 'type' field");
            }

            try {
                Class<?> clazz = Class.forName(type);
                Gson gson = new Gson();
                return gson.fromJson(in, clazz);
            } catch (ClassNotFoundException e) {
                throw new JsonParseException("Could not find class for type '" + type + "'", e);
            }
        }
    }
}
