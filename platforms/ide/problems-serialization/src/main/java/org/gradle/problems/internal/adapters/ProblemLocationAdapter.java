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

package org.gradle.problems.internal.adapters;

import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.gradle.api.problems.locations.ProblemLocation;

import java.io.IOException;

/**
 * A GSON adapter for serializing and deserializing {@link ProblemLocation} instances to/from JSON.
 */
public class ProblemLocationAdapter extends TypeAdapter<ProblemLocation> {

    private final Gson gson = new Gson();

    @Override
    public void write(JsonWriter out, ProblemLocation value) throws IOException {
        out.beginObject();
        out.name("type");
        out.value(value.getClass().getName());
        out.name("data");
        out.jsonValue(gson.toJson(value));
        out.endObject();
    }

    @Override
    public ProblemLocation read(JsonReader in) {
        String type = null;
        String data = null;
        try {
            in.beginObject();
            while (in.hasNext()) {
                String s = in.nextName();
                if (s.equals("type")) {
                    type = in.nextString();
                } else if (s.equals("data")) {
                    data = in.nextString();
                } else {
                    in.skipValue();
                }
            }
            in.endObject();
            if (type == null) {
                throw new IllegalStateException("Invalid problem location JSON: missing 'type' field");
            }
            if (data == null) {
                throw new IllegalStateException("Invalid problem location JSON: missing 'data' field");
            }

            return (ProblemLocation) gson.fromJson(data, Class.forName(type));
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid problem location JSON", ex);
        }

    }

}
