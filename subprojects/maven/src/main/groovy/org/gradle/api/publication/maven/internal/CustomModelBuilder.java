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
package org.gradle.api.publication.maven.internal;

import groovy.util.FactoryBuilderSupport;
import org.apache.maven.model.Model;
import org.codehaus.plexus.personality.plexus.lifecycle.phase.InitializationException;
import org.gradle.api.internal.artifacts.PlexusLoggerAdapter;
import org.slf4j.LoggerFactory;
import org.sonatype.maven.polyglot.execute.ExecuteManager;
import org.sonatype.maven.polyglot.execute.ExecuteManagerImpl;
import org.sonatype.maven.polyglot.groovy.builder.ModelBuilder;

import java.lang.reflect.Field;
import java.util.Map;

/**
 * This is a slightly modified version as shipped with polyglot Maven.
 */
public class CustomModelBuilder extends ModelBuilder {

    public CustomModelBuilder(Model model) {
        ExecuteManager executeManager = new ExecuteManagerImpl();
        setProp(executeManager.getClass(), executeManager, "log",
                new PlexusLoggerAdapter(LoggerFactory.getLogger(ExecuteManagerImpl.class)));
        setProp(ModelBuilder.class, this, "executeManager", executeManager);
        setProp(ModelBuilder.class, this, "log",
                new PlexusLoggerAdapter(LoggerFactory.getLogger(ModelBuilder.class)));
        try {
            initialize();
        } catch (InitializationException e) {
            throw new RuntimeException(e);
        }
        Map factories = (Map) getProp(FactoryBuilderSupport.class, this, "factories");
        factories.remove("project");
        ModelFactory modelFactory = new ModelFactory(model);
        registerFactory(modelFactory.getName(), null, modelFactory);
    }

    public static void setProp(Class c, Object obj, String fieldName, Object value) {
        try {
            Field f = c.getDeclaredField(fieldName);
            f.setAccessible(true); // solution
            f.set(obj, value); // IllegalAccessException
            // production code should handle these exceptions more gracefully
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalArgumentException e) {
           throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
           throw new RuntimeException(e);
        }
    }

    public static Object getProp(Class c, Object obj, String fieldName) {
        try {
            Field f = c.getDeclaredField(fieldName);
            f.setAccessible(true); // solution
            return f.get(obj); // IllegalAccessException
            // production code should handle these exceptions more gracefully
        } catch (NoSuchFieldException e) {
            throw new RuntimeException(e);
        } catch (IllegalArgumentException e) {
            throw new RuntimeException(e);
        } catch (IllegalAccessException e) {
            throw new RuntimeException(e);
        }
    }
}