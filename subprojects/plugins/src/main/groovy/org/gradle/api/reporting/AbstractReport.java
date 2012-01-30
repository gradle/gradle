/*
 * Copyright 2009 the original author or authors.
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

package org.gradle.api.reporting;

import groovy.lang.Closure;
import org.gradle.util.ConfigureUtil;

import java.io.File;

abstract public class AbstractReport implements Report {
                          
    private String name;

    private Object destination;

    public AbstractReport(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public String toString() {
        return String.format("Report %s", getName());
    }
    
    public File getDestination() {
        return resolveToFile(destination);
    }

    public void setDestination(Object destination) {
        this.destination = destination;
    }

    protected abstract File resolveToFile(Object file);
    
    public Report configure(Closure configure) {
        return ConfigureUtil.configure(configure, this, false);
    }

}
