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
package org.gradle.performance.android;

import com.android.builder.model.AndroidArtifact;
import com.android.builder.model.AndroidLibrary;
import com.android.builder.model.AndroidProject;
import com.android.builder.model.Dependencies;
import com.android.builder.model.JavaLibrary;
import com.android.builder.model.Variant;
import org.gradle.tooling.internal.adapter.ProtocolToModelAdapter;

import java.io.File;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;

public class Inspector {
    Set<JavaLibrary> javaLibsByEquality = new HashSet<JavaLibrary>();
    Map<File, JavaLibrary> javaLibsByFile = new HashMap<File, JavaLibrary>();
    Map<JavaLibrary, JavaLibrary> javaLibsByIdentity = new IdentityHashMap<JavaLibrary, JavaLibrary>();
    Map<Object, Object> javaLibsBackingByIdentity = new IdentityHashMap<Object, Object>();

    Set<AndroidLibrary> libsByEquality = new HashSet<AndroidLibrary>();
    Map<File, AndroidLibrary> libsByFile = new HashMap<File, AndroidLibrary>();
    Map<AndroidLibrary, AndroidLibrary> libsByIdentity = new IdentityHashMap<AndroidLibrary, AndroidLibrary>();
    Map<Object, Object> libsBackingByIdentity = new IdentityHashMap<Object, Object>();

    void inspectModel(Map<String, AndroidProject> models) {
        System.out.println("* Inspecting");
        Timer timer = new Timer();
        for (AndroidProject androidProject : models.values()) {
            if (androidProject == null) {
                continue;
            }
            inspect(androidProject);
        }
        timer.stop();
        System.out.println("Inspect took " + timer.duration());

        System.out.println("---");
        System.out.println("Android libs: " + libsByEquality.size());
        System.out.println("Android libs by file: " + libsByFile.size());
        System.out.println("Android libs by id: " + libsByIdentity.size());
        System.out.println("Android libs by id (backing): " + libsBackingByIdentity.size());
        System.out.println("Java libs: " + javaLibsByEquality.size());
        System.out.println("Java libs by file: " + javaLibsByFile.size());
        System.out.println("Java libs by id: " + javaLibsByIdentity.size());
        System.out.println("Java libs by id (backing): " + javaLibsBackingByIdentity.size());
        System.out.println("---");
    }

    private void inspect(AndroidProject androidProject) {
        for (Variant variant : androidProject.getVariants()) {
            inspect(variant.getMainArtifact().getDependencies());
            for (AndroidArtifact otherArtifact : variant.getExtraAndroidArtifacts()) {
                inspect(otherArtifact.getDependencies());
            }
        }
    }

    private void inspect(Dependencies dependencies) {
        for (AndroidLibrary androidLibrary : dependencies.getLibraries()) {
            inspect(androidLibrary);
        }
        for (JavaLibrary javaLibrary : dependencies.getJavaLibraries()) {
            inspect(javaLibrary);
        }
    }

    private void inspect(AndroidLibrary androidLibrary) {
        libsByEquality.add(androidLibrary);
        libsByFile.put(androidLibrary.getJarFile(), androidLibrary);
        libsByIdentity.put(androidLibrary, androidLibrary);
        unpack(androidLibrary, libsBackingByIdentity);
        for (AndroidLibrary library : androidLibrary.getLibraryDependencies()) {
            inspect(library);
        }
        for (JavaLibrary library : androidLibrary.getJavaDependencies()) {
            inspect(library);
        }
    }

    private void inspect(JavaLibrary javaLibrary) {
        javaLibsByEquality.add(javaLibrary);
        if (!javaLibsByFile.containsKey(javaLibrary.getJarFile())) {
            javaLibsByFile.put(javaLibrary.getJarFile(), javaLibrary);
        }
        if (!javaLibsByIdentity.containsKey(javaLibrary)) {
            javaLibsByIdentity.put(javaLibrary, javaLibrary);
        }
        unpack(javaLibrary, javaLibsBackingByIdentity);
        for (JavaLibrary library : javaLibrary.getDependencies()) {
            inspect(library);
        }
    }

    private void unpack(Object library, Map<Object, Object> objectMap) {
        Object unpacked = new ProtocolToModelAdapter().unpack(library);
        objectMap.put(unpacked, unpacked);
    }
}
