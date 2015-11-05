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
package org.gradle.api.publication.maven.internal.pom;

import groovy.util.FactoryBuilderSupport;
import org.apache.maven.model.Model;
import org.gradle.api.publication.maven.internal.ModelFactory;
import org.slf4j.LoggerFactory;
import org.sonatype.maven.polyglot.PolyglotModelManager;
import org.sonatype.maven.polyglot.execute.ExecuteManager;
import org.sonatype.maven.polyglot.execute.ExecuteManagerImpl;
import org.sonatype.maven.polyglot.groovy.GroovyMapping;
import org.sonatype.maven.polyglot.groovy.builder.ModelBuilder;
import org.sonatype.maven.polyglot.mapping.XmlMapping;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.Map;

/**
 * This is a slightly modified version as shipped with polyglot Maven.
 */
public class CustomModelBuilder extends ModelBuilder {

    public CustomModelBuilder(Model model) {
        PolyglotModelManager modelManager = new PolyglotModelManager();
        setProp(modelManager.getClass(), modelManager, "log",
                new PlexusLoggerAdapter(LoggerFactory.getLogger(PolyglotModelManager.class)));
        setProp(modelManager.getClass(), modelManager, "mappings",
                Arrays.asList(new XmlMapping(), new GroovyMapping()));
        ExecuteManager executeManager = new ExecuteManagerImpl();
        setProp(executeManager.getClass(), executeManager, "log",
                new PlexusLoggerAdapter(LoggerFactory.getLogger(ExecuteManagerImpl.class)));
        setProp(executeManager.getClass(), executeManager, "manager", modelManager);
        setProp(ModelBuilder.class, this, "executeManager", executeManager);
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