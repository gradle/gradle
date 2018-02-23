/*
 * Copyright 2013 the original author or authors.
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

function populateFooter(report) {
    $("#gradleVersion").text(report.gradleVersion);
    $("#generationDate").text(report.generationDate);
}

function initializeProjectPage(report) {
    $(document).ready(function() {
        // event handling to close the insight div
        $('#insight').on('click', '#dismissInsight', function(event) {
            $('#insight').fadeOut();
            event.preventDefault();
        });

        // creates a node of a dependency tree
        function createDependencyNode(dependency) {
            var node = {
                data : dependency.name,
                state : "open",
                attr : {'data-module' : dependency.module},
                children : []
            };
            var classes = [];
            if (dependency.alreadyRendered) {
                classes.push('alreadyRendered');
            }
            if (dependency.hasConflict) {
                classes.push('hasConflict');
            }
            if (!dependency.resolvable) {
                classes.push('unresolvable');
            }
            if (classes.length > 0) {
                node.attr['class'] = classes.join(' ');
            }
            $.each(dependency.children, function(index, dependency) {
                var dependencyNode = createDependencyNode(dependency);
                node.children.push(dependencyNode);
            });
            return node;
        }

        // finds the moduleInsight by module among the given moduleInsights and returns its insight
        function findInsight(moduleInsights, module) {
            for (var i = 0; i < moduleInsights.length; i++) {
                if (moduleInsights[i].module == module) {
                    return moduleInsights[i].insight;
                }
            }
            return null;
        }

        // creates a node of the insight tree
        function createInsightNode(dependency) {
            var node = {
                data : dependency.name + (dependency.description ? ' (' + dependency.description + ')' : ''),
                state : "open",
                attr : {},
                children : []
            }
            var classes = [];
            if (dependency.alreadyRendered) {
                classes.push('alreadyRendered');
            }
            if (!dependency.resolvable) {
                classes.push('unresolvable');
            }
            if (dependency.hasConflict) {
                classes.push('hasConflict');
            }
            if (dependency.isLeaf) {
                classes.push('leaf');
            }
            if (classes.length > 0) {
                node.attr['class'] = classes.join(' ');
            }
            $.each(dependency.children, function(index, dependency) {
                var dependencyNode = createInsightNode(dependency);
                node.children.push(dependencyNode);
            });
            return node;
        }

        // generates a tree for the given module by finding the insight among the given moduleInsights,
        // and displays the insight div
        function showModuleInsight(module, moduleInsights) {
            var $insightDiv = $('#insight');
            $insightDiv.html('');
            $insightDiv.append($('<i> </i>').attr('id', 'dismissInsight').attr('title', 'Close'));
            $insightDiv.append($('<h3>Insight for module </h3>').append(module));
            var $tree = $('<div>').addClass('insightTree');
            var insight = findInsight(moduleInsights, module);
            var nodes = [];
            $.each(insight, function(index, dependency) {
                var dependencyNode = createInsightNode(dependency);
                nodes.push(dependencyNode);
            });
            $tree.append($('<img>').attr('src', 'throbber.gif')).append('Loading...');
            $tree.jstree({
                json_data : {
                    data : nodes
                },
                themes : {
                    url : 'css/tree.css',
                    icons : false
                },
                plugins : ['json_data', 'themes']
            }).bind("loaded.jstree", function (event, data) {
                        $('li.unresolvable a').attr('title', 'This dependency could not be resolved');
                        $('li.alreadyRendered a').attr('title', 'The children of this dependency are not displayed because they have already been displayed before');
                    });
            $insightDiv.append($tree);
            $tree.on('click', 'a', function(event) {
                event.preventDefault();
            });
            $insightDiv.fadeIn();
        }

        // generates the configuration dependeny trees
        var $dependencies = $('#dependencies');
        var project = report.project;

        $dependencies.append($('<h2/>').text('Project ' + project.name));
        if (project.description) {
            $dependencies.append($('<p>').addClass('projectDescription').text(project.name));
        }

        $.each(project.configurations, function(index, configuration) {
            var $configurationDiv = $('<div/>').addClass('configuration');
            var $configurationTitle = $('<h3/>').addClass('closed').append($('<ins/>')).append(configuration.name);
            if (configuration.description) {
                $configurationTitle.append(' - ').append($('<span/>').addClass('configurationDescription').text(configuration.description));
            }
            $configurationDiv.append($configurationTitle);

            var $contentDiv = $('<div/>').addClass('configurationContent').hide();
            var $tree = $('<div>').addClass('dependencyTree');
            $contentDiv.append($tree);
            if (configuration.dependencies && configuration.dependencies.length > 0) {
                var nodes = [];
                $.each(configuration.dependencies, function(index, dependency) {
                    var dependencyNode = createDependencyNode(dependency);
                    nodes.push(dependencyNode);
                });
                $tree.append($('<img>').attr('src', 'throbber.gif')).append('Loading...');
                $tree.jstree({
                    json_data : {
                        data : nodes
                    },
                    themes : {
                        url : 'css/tree.css',
                        icons : false
                    },
                    plugins : ['json_data', 'themes']
                }).bind("loaded.jstree", function (event, data) {
                            $('li.unresolvable a').attr('title', 'This dependency could not be resolved');
                            $('li.alreadyRendered a').attr('title', 'The children of this dependency are not displayed because they have already been displayed before');
                        });
            }
            else {
                $tree.append($('<p/>').text("No dependency"));
            }

            $tree.on('click', 'a', function(event) {
                event.preventDefault();
                var module = $(this).closest('li').attr('data-module');
                showModuleInsight(module, configuration.moduleInsights);
            });

            $configurationDiv.append($contentDiv);
            $dependencies.append($configurationDiv);
        });

        // allows the titles of each dependency tree to toggle the visibility of their tree
        $dependencies.on('click', 'h3', function(event) {
            $('div.configurationContent', $(this).parent()).slideToggle();
            $(this).toggleClass('closed');
        });

        $('#projectBreacrumb').text(project.name);
        populateFooter(report);
    });
}

if (window.projectDependencyReport) {
    initializeProjectPage(window.projectDependencyReport);
}
