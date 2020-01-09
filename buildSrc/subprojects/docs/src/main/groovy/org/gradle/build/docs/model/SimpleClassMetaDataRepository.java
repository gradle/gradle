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
package org.gradle.build.docs.model;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.UnknownDomainObjectException;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import static org.apache.commons.lang.StringUtils.getLevenshteinDistance;

public class SimpleClassMetaDataRepository<T extends Attachable<T>> implements ClassMetaDataRepository<T> {
    private final Map<String, T> classes = new TreeMap<>();

    @SuppressWarnings("unchecked")
    public void load(File repoFile) {
        try {
            FileInputStream inputStream = new FileInputStream(repoFile);
            try {
                ObjectInputStream objInputStream = new ObjectInputStream(new BufferedInputStream(inputStream));
                classes.clear();
                classes.putAll((Map<String, T>) objInputStream.readObject());
            } finally {
                inputStream.close();
            }
        } catch (Exception e) {
            throw new GradleException(String.format("Could not load meta-data from %s.", repoFile), e);
        }
    }

    public void store(File repoFile) {
        try {
            FileOutputStream outputStream = new FileOutputStream(repoFile);
            try {
                ObjectOutputStream objOutputStream = new ObjectOutputStream(new BufferedOutputStream(outputStream));
                objOutputStream.writeObject(classes);
                objOutputStream.close();
            } finally {
                outputStream.close();
            }
        } catch (IOException e) {
            throw new GradleException(String.format("Could not write meta-data to %s.", repoFile), e);
        }
    }

    @Override
    public T get(String fullyQualifiedClassName) {
        T t = find(fullyQualifiedClassName);
        if (t == null) {
            throw new UnknownDomainObjectException(String.format("No meta-data is available for class '%s'. Did you mean? %s", fullyQualifiedClassName, findPossibleMatches(fullyQualifiedClassName)));
        }
        return t;
    }

    @Override
    public T find(String fullyQualifiedClassName) {
        T t = classes.get(fullyQualifiedClassName);
        if (t != null) {
            t.attach(this);
        }
        return t;
    }

    private List<String> findPossibleMatches(String fullyQualifiedClassName) {
        List<String> candidates = new ArrayList<String>();
        for (String className : classes.keySet()) {
            if (getLevenshteinDistance(fullyQualifiedClassName, className) < 8) {
                candidates.add(className);
            }
        }
        return candidates;
    }

    @Override
    public void put(String fullyQualifiedClassName, T metaData) {
        classes.put(fullyQualifiedClassName, metaData);
    }

    @Override
    public void each(Closure cl) {
        for (Map.Entry<String, T> entry : classes.entrySet()) {
            cl.call(new Object[]{entry.getKey(), entry.getValue()});
        }
    }

    public void each(Action<? super T> action) {
        for (T t : classes.values()) {
            action.execute(t);
        }
    }
}
