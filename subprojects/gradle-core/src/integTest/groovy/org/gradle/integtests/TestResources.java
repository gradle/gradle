/*
 * Copyright 2010 the original author or authors.
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

package org.gradle.integtests;

import org.gradle.util.Resources;
import org.gradle.util.TemporaryFolder;
import org.gradle.util.TestFile;
import org.junit.rules.MethodRule;
import org.junit.runners.model.FrameworkMethod;
import org.junit.runners.model.Statement;

import java.lang.reflect.Field;
import java.util.*;

/**
 * Provides access to test resources for integration testing. Looks for the following directory in the test classpath:
 * <ul> <li>${testClass}/shared</li> <li>${testClass}/${testName}</li> </ul>
 *
 * Copies the contents of each such directory into a temporary directory for the test to use.
 */
public class TestResources implements MethodRule {
    private TemporaryFolder temporaryFolder;
    private final Collection<String> extraResources;
    private final Resources resources = new Resources();

    public TestResources(String... extraResources) {
        this.extraResources = Arrays.asList(extraResources);
    }

    public TestFile getDir() {
        return temporaryFolder.getDir();
    }

    public Statement apply(Statement base, final FrameworkMethod method, Object target) {
        final Statement statement = resources.apply(base, method, target);
        temporaryFolder = findTempDir(target);
        return new Statement() {
            @Override
            public void evaluate() throws Throwable {
                String className = method.getMethod().getDeclaringClass().getSimpleName();
                maybeCopy(String.format("%s/shared", className));
                maybeCopy(String.format("%s/%s", className, method.getName()));
                for (String extraResource : extraResources) {
                    maybeCopy(extraResource);
                }
                statement.evaluate();
            }
        };
    }

    private TemporaryFolder findTempDir(Object target) {
        GradleDistribution dist = findField(target, GradleDistribution.class);
        if (dist != null) {
            return dist.getTemporaryFolder();
        }
        TemporaryFolder folder = findField(target, TemporaryFolder.class);
        if (folder != null) {
            return folder;
        }
        throw new RuntimeException(String.format(
                "Could not find a GradleDistribution or TemporaryFolder field for test class %s.",
                target.getClass().getSimpleName()));
    }

    private <T> T findField(Object target, Class<T> type) {
        List<T> matches = new ArrayList<T>();
        for (Class<?> cl = target.getClass(); cl != Object.class; cl = cl.getSuperclass()) {
            for (Field field : cl.getDeclaredFields()) {
                if (type.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    try {
                        matches.add(type.cast(field.get(target)));
                    } catch (IllegalAccessException e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        }
        if (matches.isEmpty()) {
            return null;
        }
        if (matches.size() > 1) {
            throw new RuntimeException(String.format("Multiple %s fields found for test class %s.",
                    type.getSimpleName(), target.getClass().getSimpleName()));
        }
        return matches.get(0);
    }

    private void maybeCopy(String resource) {
        TestFile dir = resources.findResource(resource);
        if (dir != null) {
            dir.copyTo(getDir());
        }
    }
}
