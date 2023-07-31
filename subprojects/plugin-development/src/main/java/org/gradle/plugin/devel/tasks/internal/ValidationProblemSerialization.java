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
import com.google.gson.reflect.TypeToken;
import org.gradle.api.problems.interfaces.DocLink;
import org.gradle.api.problems.internal.DefaultProblem;
import org.gradle.internal.deprecation.Documentation;
import org.gradle.internal.reflect.validation.TypeValidationProblemRenderer;

import java.lang.reflect.Type;
import java.util.List;
import java.util.stream.Stream;

public class ValidationProblemSerialization {
    public static List<DefaultProblem> parseMessageList(String lines) {
        GsonBuilder gsonBuilder = new GsonBuilder();
        gsonBuilder.registerTypeAdapter(DocLink.class, new JsonDeserializer<DocLink>() {
            @Override
            public DocLink deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
                JsonObject jsonObject = (JsonObject) json;
                return Documentation.userManual(jsonObject.get("page").getAsString(), jsonObject.get("section").getAsString());
            }
        });
//        gsonBuilder.registerTypeAdapter(Problem.class, (InstanceCreator<Problem>) type -> new DefaultProblem());
        Gson gson = gsonBuilder.create();
        Type type = new TypeToken<List<DefaultProblem>>(){}.getType();
        return gson.fromJson(lines, type);
    }

    public static Stream<String> toMessages(List<DefaultProblem> problems) {
        return toPlainMessage(problems);
//            .map(msg -> String.format("%n  - %s", msg));
    }

    public static Stream<String> toPlainMessage(List<DefaultProblem> problems) {
        return problems.stream()
            .map(problem -> problem.getSeverity() + ": " + TypeValidationProblemRenderer.renderMinimalInformationAbout(problem));
    }
}
