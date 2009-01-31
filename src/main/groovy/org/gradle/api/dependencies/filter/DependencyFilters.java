/*
 * Copyright 2007-2009 the original author or authors.
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
package org.gradle.api.dependencies.filter;

import org.gradle.api.dependencies.Dependency;
import org.gradle.api.filter.FilterSpec;
import org.gradle.api.filter.AndSpec;
import org.gradle.api.filter.OrSpec;
import org.gradle.api.filter.NotSpec;

import java.util.List;
import java.util.ArrayList;
import java.util.Collection;

/**
 * @author Hans Dockter
 */
public class DependencyFilters {
    public static ConfSpec confs(String... confs) {
        return new ConfSpec(true, confs);
    }

    public static ConfSpec confsWithoutExtensions(String... confs) {
        return new ConfSpec(false, confs);
    }

    public static TypeSpec type(Type type) {
        return new TypeSpec(type);
    }
}