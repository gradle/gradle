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
import com.google.gson.JsonDeserializationContext;
import com.google.gson.JsonDeserializer;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParseException;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;
import org.gradle.api.problems.ProblemGroup;
import org.gradle.api.problems.ProblemId;
import org.gradle.api.problems.internal.AdditionalData;
import org.gradle.api.problems.internal.DefaultDeprecationData;
import org.gradle.api.problems.internal.DefaultFileLocation;
import org.gradle.api.problems.internal.DefaultGeneralData;
import org.gradle.api.problems.internal.DefaultLineInFileLocation;
import org.gradle.api.problems.internal.DefaultOffsetInFileLocation;
import org.gradle.api.problems.internal.DefaultPluginIdLocation;
import org.gradle.api.problems.internal.DefaultProblem;
import org.gradle.api.problems.internal.DefaultProblemGroup;
import org.gradle.api.problems.internal.DefaultProblemId;
import org.gradle.api.problems.internal.DefaultPropertyTraceData;
import org.gradle.api.problems.internal.DefaultTaskPathLocation;
import org.gradle.api.problems.internal.DefaultTypeValidationData;
import org.gradle.api.problems.internal.DeprecationData;
import org.gradle.api.problems.internal.DocLink;
import org.gradle.api.problems.internal.FileLocation;
import org.gradle.api.problems.internal.GeneralData;
import org.gradle.api.problems.internal.LineInFileLocation;
import org.gradle.api.problems.internal.OffsetInFileLocation;
import org.gradle.api.problems.internal.Problem;
import org.gradle.api.problems.internal.ProblemLocation;
import org.gradle.api.problems.internal.PropertyTraceData;
import org.gradle.api.problems.internal.TypeValidationData;
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.lang.reflect.Constructor;
import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Stream;

@Nonnull
public class ValidationProblemSerialization {
    private static final GsonBuilder GSON_BUILDER = createGsonBuilder();

    public static List<? extends Problem> parseMessageList(String lines) {
        Gson gson = GSON_BUILDER.create();
        Type type = new TypeToken<List<DefaultProblem>>() {}.getType();
        return gson.<List<DefaultProblem>>fromJson(lines, type);
    }

    public static GsonBuilder createGsonBuilder() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapterFactory(new ProblemReportAdapterFactory());
        gsonBuilder.registerTypeAdapter(ProblemId.class, new ProblemIdInstanceCreator());
        gsonBuilder.registerTypeHierarchyAdapter(DocLink.class, new DocLinkAdapter());
        gsonBuilder.registerTypeHierarchyAdapter(ProblemLocation.class, new LocationAdapter());
        gsonBuilder.registerTypeHierarchyAdapter(AdditionalData.class, new AdditionalDataAdapter());
        gsonBuilder.registerTypeAdapterFactory(new ThrowableAdapterFactory());

        return gsonBuilder;
    }


    public static Stream<String> toPlainMessage(List<? extends Problem> problems) {
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

    private static class ProblemIdInstanceCreator implements JsonDeserializer<ProblemId>, JsonSerializer<ProblemId> {

        @Override
        public ProblemId deserialize(JsonElement jsonElement, Type type, JsonDeserializationContext jsonDeserializationContext) throws JsonParseException {
            JsonObject problemObject = jsonElement.getAsJsonObject();
            String name = problemObject.get("name").getAsString();
            String displayName = problemObject.get("displayName").getAsString();
            ProblemGroup group = deserializeGroup(problemObject.get("group"));
            return new DefaultProblemId(name, displayName, group);
        }

        private static ProblemGroup deserializeGroup(JsonElement groupObject) {
            JsonObject group = groupObject.getAsJsonObject();
            String name = group.get("name").getAsString();
            String displayName = group.get("displayName").getAsString();
            JsonElement parent = group.get("parent");
            if (parent == null) {
                return new DefaultProblemGroup(name, displayName);
            }
            return new DefaultProblemGroup(name, displayName, deserializeGroup(parent));
        }

        @Override
        public JsonElement serialize(ProblemId problemId, Type type, JsonSerializationContext jsonSerializationContext) {
            JsonObject result = new JsonObject();
            result.addProperty("name", problemId.getName());
            result.addProperty("displayName", problemId.getDisplayName());
            result.add("group", serializeGroup(problemId.getGroup()));
            return result;
        }


        private static JsonObject serializeGroup(ProblemGroup group) {
            JsonObject groupObject = new JsonObject();
            groupObject.addProperty("name", group.getName());
            groupObject.addProperty("displayName", group.getDisplayName());
            ProblemGroup parent = group.getParent();
            if (parent != null) {
                groupObject.add("parent", serializeGroup(parent));
            }
            return groupObject;
        }
    }

    private static class AdditionalDataAdapter extends TypeAdapter<AdditionalData> {
        public static final String PROPERTY_TRACE_DATA = "propertyTraceData";
        public static final String PROPERTY_TRACE = "propertyTrace";
        public static final String ADDITIONAL_DATA_TYPE = "type";
        public static final String DEPRECATION_DATA = "deprecationData";
        public static final String TYPE_VALIDATION_DATA = "typeValidationData";
        public static final String GENERAL_DATA = "generalData";
        public static final String FEATURE_USAGE = "featureUsage";
        public static final String PLUGIN_ID = "pluginId";
        public static final String PROPERTY_NAME = "propertyName";
        public static final String FUNCTION_NAME = "functionName";
        public static final String PARENT_PROPERTY_NAME = "parentPropertyName";
        public static final String TYPE_NAME = "typeName";
        public static final String GENERAL_DATA_DATA = "data";

        @Override
        public void write(JsonWriter out, AdditionalData value) throws IOException {
            if (value == null) {
                out.nullValue();
                return;
            }
            out.beginObject();
            if (value instanceof DeprecationData) {
                out.name(ADDITIONAL_DATA_TYPE).value(DEPRECATION_DATA);
                out.name(FEATURE_USAGE).value(((DeprecationData) value).getType().name());
            } else if (value instanceof TypeValidationData) {
                out.name(ADDITIONAL_DATA_TYPE).value(TYPE_VALIDATION_DATA);
                TypeValidationData typeValidationData = (TypeValidationData) value;
                out.name(PLUGIN_ID).value(typeValidationData.getPluginId());
                out.name(PROPERTY_NAME).value(typeValidationData.getPropertyName());
                out.name(FUNCTION_NAME).value(typeValidationData.getFunctionName());
                out.name(PARENT_PROPERTY_NAME).value(typeValidationData.getParentPropertyName());
                out.name(TYPE_NAME).value(typeValidationData.getTypeName());
            } else if (value instanceof GeneralData) {
                out.name(ADDITIONAL_DATA_TYPE).value(GENERAL_DATA);
                out.name(GENERAL_DATA_DATA);
                out.beginObject();
                Map<String, String> map = ((GeneralData) value).getAsMap();
                for (String key : map.keySet()) {
                    out.name(key).value(map.get(key));
                }
                out.endObject();
            } else if (value instanceof PropertyTraceData) {
                out.name(ADDITIONAL_DATA_TYPE).value(PROPERTY_TRACE_DATA);
                out.name(PROPERTY_TRACE).value(((PropertyTraceData) value).getTrace());
            }
            out.endObject();
        }

        @Override
        public AdditionalData read(JsonReader in) throws IOException {
            if (!in.hasNext()) {
                return null;
            }
            in.beginObject();
            try {
                String type = null;
                String featureUsage = null;
                String pluginId = null;
                String propertyName = null;
                String functionName = null;
                String parentPropertyName = null;
                String typeName = null;
                String name;
                Map<String, String> generalData = null;
                String propertyTrace = null;

                while (in.hasNext()) {
                    name = in.nextName();
                    switch (name) {
                        case ADDITIONAL_DATA_TYPE: {
                            type = in.nextString();
                            break;
                        }
                        case FEATURE_USAGE: {
                            featureUsage = in.nextString();
                            break;
                        }
                        case PLUGIN_ID: {
                            pluginId = in.nextString();
                            break;
                        }
                        case PROPERTY_NAME: {
                            propertyName = in.nextString();
                            break;
                        }
                        case FUNCTION_NAME: {
                            functionName = in.nextString();
                            break;
                        }
                        case PARENT_PROPERTY_NAME: {
                            parentPropertyName = in.nextString();
                            break;
                        }
                        case TYPE_NAME: {
                            typeName = in.nextString();
                            break;
                        }
                        case PROPERTY_TRACE: {
                            propertyTrace = in.nextString();
                            break;
                        }
                        case GENERAL_DATA_DATA: {
                            try {
                                in.beginObject();
                                generalData = new HashMap<>();
                                while (in.hasNext()) {
                                    String key = in.nextName();
                                    String value = in.nextString();
                                    generalData.put(key, value);
                                }
                            } finally {
                                in.endObject();
                            }
                            break;
                        }
                        default:
                            in.skipValue();
                    }
                }
                if (type == null) {
                    throw new JsonParseException("type must not be null");
                }
                return createAdditionalData(type, featureUsage, pluginId, propertyName, functionName, parentPropertyName, typeName, generalData, propertyTrace);
            } finally {
                in.endObject();
            }
        }

        private static @Nonnull AdditionalData createAdditionalData(String type, String featureUsage, String pluginId, String propertyName, String methodName, String parentPropertyName, String typeName, Map<String, String> generalData, String propertyTrace) {
            switch (type) {
                case DEPRECATION_DATA:
                    return new DefaultDeprecationData(DeprecationData.Type.valueOf(featureUsage));
                case TYPE_VALIDATION_DATA:
                    return new DefaultTypeValidationData(
                        pluginId,
                        propertyName,
                        methodName,
                        parentPropertyName,
                        typeName
                    );
                case GENERAL_DATA:
                    return new DefaultGeneralData(generalData);
                case PROPERTY_TRACE_DATA:
                    return new DefaultPropertyTraceData(propertyTrace);
                default:
                    throw new JsonParseException("Unknown type: " + type);
            }
        }
    }
}
