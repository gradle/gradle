package org.gradle.api.tasks.ide.eclipse;

import org.gradle.util.WrapUtil;

import java.util.ArrayList;
import java.util.List;

public enum ProjectType {
    JAVA {
        public List<String> buildCommandNames() {
            return WrapUtil.toList("org.eclipse.jdt.core.javabuilder");
        }
        public List<String> natureNames() {
            return WrapUtil.toList("org.eclipse.jdt.core.javanature");
        }},
    SIMPLE {
        public List<String> buildCommandNames() {
            return new ArrayList<String>();
        }
        public List<String> natureNames() {
            return new ArrayList<String>();
        }
    },
    WTP_MODULE {
	public List<String> buildCommandNames() {
	    return WrapUtil.toList("org.eclipse.jdt.core.javabuilder", "org.eclipse.wst.common.project.facet.core.builder", "org.eclipse.wst.validation.validationbuilder");
        }
        public List<String> natureNames() {
            return WrapUtil.toList("org.eclipse.jdt.core.javanature", "org.eclipse.wst.common.project.facet.core.nature", "org.eclipse.wst.common.modulecore.ModuleCoreNature");
        }
    },
    WTP_WEBAPP {
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
