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

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.gradle.api.problems.DocLink;
import org.gradle.api.problems.internal.DefaultDocLink;

import java.io.IOException;

/**
 * A GSON adapter for serializing and deserializing {@link DocLink} instances to/from JSON.
 */
public class DocLinkAdapter extends TypeAdapter<DocLink> {

    @Override
    public void write(JsonWriter out, DocLink value) throws IOException {
        out.beginObject();
        out.name("url");
        out.value(value.getUrl());
        out.name("message");
        out.value(value.getConsultDocumentationMessage());
        out.endObject();
    }

    @Override
    public DocLink read(JsonReader in) throws IOException {
        String url = null;
        String message = null;
        try {
            in.beginObject();
            while (in.hasNext()) {
                String s = in.nextName();
                if (s.equals("url")) {
                    url = in.nextString();
                } else if (s.equals("message")) {
                    message = in.nextString();
                } else {
                    in.skipValue();
                }
            }

            if (url == null) {
                throw new IllegalStateException("Invalid problem documentation link JSON: missing 'url' field");
            }
            if (message == null) {
                throw new IllegalStateException("Invalid problem documentation link JSON: missing 'message' field");
            }
            return new DefaultDocLink(url, message);
        } catch (Exception ex) {
            throw new IllegalStateException("Invalid problem documentation link JSON", ex);
        }
    }
}
