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

package org.gradle.api.tasks.ide.eclipse;

import org.gradle.util.WrapUtil;

import java.util.ArrayList;
import java.util.List;

public abstract class ProjectType {
    public static final ProjectType JAVA = new ProjectType() {
        public List<String> buildCommandNames() {
            return WrapUtil.toList("org.eclipse.jdt.core.javabuilder");
        }
        public List<String> natureNames() {
            return WrapUtil.toList("org.eclipse.jdt.core.javanature");
        }
	};
    public static final ProjectType GROOVY = new ProjectType() {
        public List<String> buildCommandNames() {
            return WrapUtil.toList("org.eclipse.jdt.core.javabuilder");
        }
        public List<String> natureNames() {
            return WrapUtil.toList("org.eclipse.jdt.groovy.core.groovyNature", "org.eclipse.jdt.core.javanature");
        }
    };
    public static final ProjectType SIMPLE = new ProjectType() {
        public List<String> buildCommandNames() {
            return new ArrayList<String>();
        }
        public List<String> natureNames() {
            return new ArrayList<String>();
        }
    };
    public static final ProjectType WTP_MODULE = new ProjectType() {
		public List<String> buildCommandNames() {
			return WrapUtil.toList("org.eclipse.jdt.core.javabuilder", "org.eclipse.wst.common.project.facet.core.builder", "org.eclipse.wst.validation.validationbuilder");
        }
        public List<String> natureNames() {
            return WrapUtil.toList("org.eclipse.jdt.core.javanature", "org.eclipse.wst.common.project.facet.core.nature", "org.eclipse.wst.common.modulecore.ModuleCoreNature");
        }
    };
    public static final ProjectType WTP_WEBAPP = new ProjectType() {
		public List<String> buildCommandNames() {
			return WrapUtil.toList("org.eclipse.jdt.core.javabuilder", "org.eclipse.wst.common.project.facet.core.builder", "org.eclipse.wst.validation.validationbuilder");
        }
        public List<String> natureNames() {
            return WrapUtil.toList("org.eclipse.jdt.core.javanature", "org.eclipse.wst.common.project.facet.core.nature", "org.eclipse.wst.common.modulecore.ModuleCoreNature");
        }
    };

    public abstract List<String> buildCommandNames();

    public abstract List<String> natureNames();
}
