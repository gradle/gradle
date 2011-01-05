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


package org.gradle.groovy.scripts

import org.gradle.api.internal.project.ServiceRegistry
import org.gradle.api.internal.file.FileOperations
import org.gradle.logging.StandardOutputCapture

/**
 * @author Hans Dockter
 *
 */
abstract class BasicScript extends org.gradle.groovy.scripts.Script implements org.gradle.api.Script, FileOperations {
    private StandardOutputCapture standardOutputCapture
    private Object target

    void init(Object target, ServiceRegistry services) {
        standardOutputCapture = services.get(StandardOutputCapture.class)
        this.target = target
    }

    def Object getScriptTarget() {
        return target
    }

    def StandardOutputCapture getStandardOutputCapture() {
        return standardOutputCapture
    }

    void setProperty(String property, newValue) {
        if ("metaClass" == property) {
            setMetaClass((MetaClass) newValue)
        } else if ("scriptTarget" == property) {
            target = newValue
        } else {
            target."$property" = newValue
        }
    }

    def propertyMissing(String property) {
        if ('out' == property) {
            System.out
        } else {
            target."$property"
        }
    }

    def hasProperty(String property) {
        target.hasProperty(property)
    }

    def methodMissing(String name, Object params) {
        return target.invokeMethod(name, params)
    }
}


