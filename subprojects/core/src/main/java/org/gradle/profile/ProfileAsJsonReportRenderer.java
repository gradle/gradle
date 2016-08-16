/*
 * Copyright 2016 the original author or authors.
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
package org.gradle.profile;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.gradle.api.UncheckedIOException;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;


public class ProfileAsJsonReportRenderer {

    public void writeTo(BuildProfile buildProfile, File file) {
        try {
            File parentFile = file.getParentFile();

            if (parentFile != null) {
                if (!parentFile.mkdirs() && !parentFile.isDirectory()) {
                    throw new IOException(String.format("Unable to create directory '%s'", parentFile));
                }
            }

            ProfileAsJsonReportBindings jsonReportBindings = new ProfileAsJsonReportBindings(buildProfile);

            Gson gson = new GsonBuilder()
                .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
                .serializeNulls()
                .setPrettyPrinting()
                .create();

            FileWriter fileWriter = new FileWriter(file);
            try {
                gson.toJson(jsonReportBindings, fileWriter);
            } finally {
                fileWriter.close();
            }

        } catch (Exception e) {
            throw new UncheckedIOException(String.format("Could not write to file '%s'.", file), e);
        }
    }
}
