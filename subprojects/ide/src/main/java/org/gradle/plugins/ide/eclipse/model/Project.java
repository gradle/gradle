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
package org.gradle.plugins.ide.eclipse.model;

import com.google.common.base.Objects;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import groovy.util.Node;
import org.gradle.api.Incubating;
import org.gradle.internal.xml.XmlTransformer;
import org.gradle.plugins.ide.eclipse.model.internal.DefaultResourceFilter;
import org.gradle.plugins.ide.eclipse.model.internal.DefaultResourceFilterMatcher;
import org.gradle.plugins.ide.internal.generator.XmlPersistableConfigurationObject;

import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.google.common.base.Strings.isNullOrEmpty;
import static com.google.common.base.Strings.nullToEmpty;
import static org.gradle.plugins.ide.eclipse.model.ResourceFilterAppliesTo.*;
import static org.gradle.plugins.ide.eclipse.model.ResourceFilterType.EXCLUDE_ALL;
import static org.gradle.plugins.ide.eclipse.model.ResourceFilterType.INCLUDE_ONLY;

/**
 * Represents the customizable elements of an eclipse project file. (via XML hooks everything is customizable).
 */
public class Project extends XmlPersistableConfigurationObject {

    public static final String PROJECT_FILE_NAME = ".project";

    private String name;
    private String comment;
    private Set<String> referencedProjects = Sets.newLinkedHashSet();
    private List<String> natures = Lists.newArrayList();
    private List<BuildCommand> buildCommands = Lists.newArrayList();
    private Set<Link> linkedResources = Sets.newLinkedHashSet();
    private Set<ResourceFilter> resourceFilters = Sets.newLinkedHashSet();

    public Project(XmlTransformer xmlTransformer) {
        super(xmlTransformer);
    }

    @Override
    protected String getDefaultResourceName() {
        return "defaultProject.xml";
    }

    /**
     * The name used for the name of the eclipse project
     */
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    /**
     * A comment used for the eclipse project
     */
    public String getComment() {
        return comment;
    }

    public void setComment(String comment) {
        this.comment = comment;
    }

    /**
     * The referenced projects of this Eclipse project.
     */
    public Set<String> getReferencedProjects() {
        return referencedProjects;
    }

    public void setReferencedProjects(Set<String> referencedProjects) {
        this.referencedProjects = referencedProjects;
    }

    /**
     * The natures to be added to this Eclipse project.
     */
    public List<String> getNatures() {
        return natures;
    }

    public void setNatures(List<String> natures) {
        this.natures = natures;
    }

    /**
     * The build commands to be added to this Eclipse project.
     */
    public List<BuildCommand> getBuildCommands() {
        return buildCommands;
    }

    public void setBuildCommands(List<BuildCommand> buildCommands) {
        this.buildCommands = buildCommands;
    }

    /**
     * The linkedResources to be added to this Eclipse project.
     */
    public Set<Link> getLinkedResources() {
        return linkedResources;
    }

    public void setLinkedResources(Set<Link> linkedResources) {
        this.linkedResources = linkedResources;
    }

    /*
     * The resource filters of this Eclipse project.
     *
     * @since 3.5
     */
    @Incubating
    public Set<ResourceFilter> getResourceFilters() {
        return resourceFilters;
    }

    /*
     * Sets the resource filters of this Eclipse project.
     *
     * @since 3.5
     */
    @Incubating
    public void setResourceFilters(Set<ResourceFilter> resourceFilters) {
        this.resourceFilters = resourceFilters;
    }

    public Object configure(EclipseProject eclipseProject) {
        name = nullToEmpty(eclipseProject.getName());
        comment = nullToEmpty(eclipseProject.getComment());
        referencedProjects.addAll(eclipseProject.getReferencedProjects());
        natures.addAll(eclipseProject.getNatures());
        natures = Lists.newArrayList(Sets.newLinkedHashSet(natures));
        buildCommands.addAll(eclipseProject.getBuildCommands());
        buildCommands = Lists.newArrayList(Sets.newLinkedHashSet(buildCommands));
        resourceFilters.addAll(eclipseProject.getResourceFilters());
        return linkedResources.addAll(eclipseProject.getLinkedResources());
    }

    @Override
    protected void load(Node xml) {
        Node nameNode = findFirstChildNamed(xml, "name");
        name = nameNode != null ? nameNode.text() : "";
        Node commentNode = findFirstChildNamed(xml, "comment");
        comment = commentNode != null ? commentNode.text() : "";
        readReferencedProjects();
        readNatures();
        readBuildCommands();
        readLinkedResources();
        readResourceFilters();
    }

    private void readReferencedProjects() {
        for (Node projectNode : getChildren(findFirstChildNamed(getXml(), "projects"), "project")) {
            referencedProjects.add(projectNode.text());
        }
    }

    private void readNatures() {
        for (Node natureNode : getChildren(findFirstChildNamed(getXml(), "natures"), "nature")) {
            natures.add(natureNode.text());
        }
    }

    private void readBuildCommands() {
        for (Node commandNode : getChildren(findFirstChildNamed(getXml(), "buildSpec"), "buildCommand")) {
            String name = findFirstChildNamed(commandNode, "name").text();
            Map<String, String> arguments = Maps.newHashMap();
            for (Node dictionaryNode : getChildren(findFirstChildNamed(commandNode, "arguments"), "dictionary")) {
                String key = findFirstChildNamed(dictionaryNode, "key").text();
                String value = findFirstChildNamed(dictionaryNode, "value").text();
                arguments.put(key, value);
            }
            buildCommands.add(new BuildCommand(name, arguments));
        }
    }

    private void readLinkedResources() {
        for (Node linkNode : getChildren(findFirstChildNamed(getXml(), "linkedResources"), "link")) {
            Node nameNode = findFirstChildNamed(linkNode, "name");
            Node typeNode = findFirstChildNamed(linkNode, "type");
            Node locationNode = findFirstChildNamed(linkNode, "location");
            Node locationUriNode = findFirstChildNamed(linkNode, "locationURI");
            linkedResources.add(new Link(
                nameNode != null ? nameNode.text() : null,
                typeNode != null ? typeNode.text() : null,
                locationNode != null ? locationNode.text() : null,
                locationUriNode != null ? locationUriNode.text() : null
            ));
        }
    }

    private void readResourceFilters() {
        for (Node filterNode : getChildren(findFirstChildNamed(getXml(), "filteredResources"), "filter")) {
            Node typeNode = findFirstChildNamed(filterNode, "type");
            Node matcherNode = findFirstChildNamed(filterNode, "matcher");
            String typeString = typeNode != null ? typeNode.text() : null;
            int typeBitmask = Integer.parseInt(typeString);
            ResourceFilterAppliesTo appliesTo = resourceFilterTypeBitmaskToAppliesTo(typeBitmask);
            ResourceFilterType type = resourceFilterTypeBitmaskToType(typeBitmask);
            boolean recursive = isResourceFilterTypeBitmaskRecursive(typeBitmask);
            ResourceFilterMatcher matcher = readResourceFilterMatcher(matcherNode);
            resourceFilters.add(new DefaultResourceFilter(
                appliesTo,
                type,
                recursive,
                matcher
            ));
        }
    }

    @Override
    protected void store(Node xml) {
        for (String childNodeName : Arrays.asList("name", "comment", "projects", "natures", "buildSpec", "linkedResources", "filteredResources")) {
            Node childNode = findFirstChildNamed(xml, childNodeName);
            if (childNode != null) {
                xml.remove(childNode);
            }
        }
        xml.appendNode("name", nullToEmpty(name));
        xml.appendNode("comment", nullToEmpty(comment));
        addReferencedProjectsToXml();
        addNaturesToXml();
        addBuildSpecToXml();
        addLinkedResourcesToXml();
        addResourceFiltersToXml();
    }

    private void addReferencedProjectsToXml() {
        Node referencedProjectsNode = getXml().appendNode("projects");
        for (String projectName : referencedProjects) {
            referencedProjectsNode.appendNode("project", projectName);
        }
    }

    private void addNaturesToXml() {
        Node naturesNode = getXml().appendNode("natures");
        for (String nature : natures) {
            naturesNode.appendNode("nature", nature);
        }
    }

    private void addBuildSpecToXml() {
        Node buildSpec = getXml().appendNode("buildSpec");
        for (BuildCommand command : buildCommands) {
            Node commandNode = buildSpec.appendNode("buildCommand");
            commandNode.appendNode("name", command.getName());
            Node argumentsNode = commandNode.appendNode("arguments");
            for (Map.Entry<String, String> argument : command.getArguments().entrySet()) {
                Node dictionaryNode = argumentsNode.appendNode("dictionary");
                dictionaryNode.appendNode("key", argument.getKey());
                dictionaryNode.appendNode("value", argument.getValue());
            }
        }
    }

    private void addLinkedResourcesToXml() {
        Node parent = getXml().appendNode("linkedResources");
        for (Link link : linkedResources) {
            Node linkNode = parent.appendNode("link");
            linkNode.appendNode("name", link.getName());
            linkNode.appendNode("type", link.getType());
            if (!isNullOrEmpty(link.getLocation())) {
                linkNode.appendNode("location", link.getLocation());
            }
            if (!isNullOrEmpty(link.getLocationUri())) {
                linkNode.appendNode("locationURI", link.getLocationUri());
            }
        }
    }

    private void addResourceFiltersToXml() {
        Node parent = getXml().appendNode("filteredResources");
        int filterId = 1;
        for (ResourceFilter resourceFilter : resourceFilters) {
            Node filterNode = parent.appendNode("filter");
            filterNode.appendNode("id", filterId++);
            int type = getResourceFilterType(resourceFilter);
            filterNode.appendNode("type", type);
            filterNode.appendNode("name"); // always empty
            addResourceFilterMatcherToXml(filterNode, resourceFilter.getMatcher());
        }
    }

    private void addResourceFilterMatcherToXml(Node parent, ResourceFilterMatcher matcher) {
            Node matcherNode = parent.appendNode("matcher");
             matcherNode.appendNode("id", matcher.getId());
            // A matcher may have either arguments or children, but not both
            if (!isNullOrEmpty(matcher.getArguments())) {
                matcherNode.appendNode("arguments", matcher.getArguments());
            } else if (!matcher.getChildren().isEmpty()) {
                Node argumentsNode = matcherNode.appendNode("arguments");
                for (ResourceFilterMatcher m : matcher.getChildren()) {
                    addResourceFilterMatcherToXml(argumentsNode, m);
                }
            }
    }

    private int getResourceFilterType(ResourceFilter resourceFilter) {
        int type = 0;
        switch (resourceFilter.getType()) {
            case INCLUDE_ONLY:
                type |= 1;
                break;
            case EXCLUDE_ALL:
                type |= 2;
                break;
        }
        switch (resourceFilter.getAppliesTo()) {
            case FILES:
                type |= 4;
                break;
            case FOLDERS:
                type |= 8;
                break;
            case FILES_AND_FOLDERS:
                type |= 12;
                break;
        }
        if (resourceFilter.isRecursive()) {
            type |= 16;
        }
        return type;
    }

    private ResourceFilterAppliesTo resourceFilterTypeBitmaskToAppliesTo(int type) {
        Preconditions.checkArgument(type >= 0);
        if (((type & 8) != 0) && ((type & 4) != 0)) { // order is important here, this must come first
            return FILES_AND_FOLDERS;
        }
        if ((type & 8) != 0) {
            return FOLDERS;
        }
        if ((type & 4) != 0) {
            return FILES;
        }
        return null;
    }

    private ResourceFilterType resourceFilterTypeBitmaskToType(int type) {
        Preconditions.checkArgument(type >= 0);
        if ((type & 1) != 0) {
            return INCLUDE_ONLY;
        }
        if ((type & 2) != 0) {
            return EXCLUDE_ALL;
        }
        return null;
    }

    private boolean isResourceFilterTypeBitmaskRecursive(int type) {
        Preconditions.checkArgument(type >= 0);
        return (type & 16) != 0;
    }

    private ResourceFilterMatcher readResourceFilterMatcher(Node matcherNode) {
        if (matcherNode == null) {
            return null;
        }
        Node idNode = findFirstChildNamed(matcherNode, "id");
        Node argumentsNode = findFirstChildNamed(matcherNode, "arguments");
        String arguments = null;
        Set<ResourceFilterMatcher> children = Sets.newLinkedHashSet();
        // A matcher may have either a text argument or children matcher nodes, but not both
        if (argumentsNode != null && findFirstChildNamed(argumentsNode, "matcher") != null) {
            for (Node childMatcherNode : getChildren(argumentsNode, "matcher")) {
                ResourceFilterMatcher childMatcher = readResourceFilterMatcher(childMatcherNode);
                if (childMatcher != null) {
                    children.add(childMatcher);
                }
            }
        } else {
            arguments = argumentsNode != null ? argumentsNode.text() : null;
        }
        return new DefaultResourceFilterMatcher(
            idNode != null ? idNode.text() : null,
            arguments,
            children
        );
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!getClass().equals(o.getClass())) {
            return false;
        }
        Project project = (Project) o;
        return Objects.equal(buildCommands, project.buildCommands)
            && Objects.equal(comment, project.comment)
            && Objects.equal(linkedResources, project.linkedResources)
            && Objects.equal(resourceFilters, project.resourceFilters)
            && Objects.equal(name, project.name)
            && Objects.equal(natures, project.natures)
            && Objects.equal(referencedProjects, project.referencedProjects);
    }

    @Override
    public int hashCode() {
        int result;
        result = name != null ? name.hashCode() : 0;
        result = 31 * result + (comment != null ? comment.hashCode() : 0);
        result = 31 * result + (referencedProjects != null ? referencedProjects.hashCode() : 0);
        result = 31 * result + (natures != null ? natures.hashCode() : 0);
        result = 31 * result + (buildCommands != null ? buildCommands.hashCode() : 0);
        result = 31 * result + (linkedResources != null ? linkedResources.hashCode() : 0);
        result = 31 * result + (resourceFilters != null ? resourceFilters.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "Project{"
            + "name='" + name + "\'"
            + ", comment='" + comment + "\'"
            + ", referencedProjects=" + referencedProjects
            + ", natures=" + natures
            + ", buildCommands=" + buildCommands
            + ", linkedResources=" + linkedResources
            + ", resourceFilters=" + resourceFilters
            + "}";
    }
}
