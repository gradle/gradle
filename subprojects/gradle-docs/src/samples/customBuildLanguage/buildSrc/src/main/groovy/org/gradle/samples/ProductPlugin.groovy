package org.gradle.samples

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.plugins.ProjectPluginsContainer
import org.gradle.api.tasks.bundling.Zip

/**
 * A plugin which configures a product project. Each product is composed of several product modules.
 */
class ProductPlugin implements Plugin {

    void use(Project project, ProjectPluginsContainer projectPluginsHandler) {
        project.configure(project) {
            usePlugin 'base'

            def pluginConvention = new ProductPluginConvention()
            convention.plugins.product = pluginConvention
            pluginConvention.distSrcDirs << rootProject.file('src/dist')
            pluginConvention.distSrcDirs << project.file('src/dist')

            configurations {
                runtime
            }
            tasks.add(name: 'dist', type: Zip) {
            }

            afterEvaluate {
                ProductDefinition product = pluginConvention.product
                product.modules.each {p ->
                    dependencies { runtime delegate.project(p.path) }
                }
                archivesBaseName = "some-company-${product.displayName.replaceAll('\\s+', '-').toLowerCase()}"
                dist {
                    into('lib') {
                        from configurations.runtime
                    }
                    from(pluginConvention.distSrcDirs) {
                        filter(org.apache.tools.ant.filters.ReplaceTokens, tokens: [
                                productName: product.displayName,
                                version: version
                        ])
                    }
                }
            }
        }
    }
}