package org.gradle.samples

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.bundling.Zip

/**
 * A plugin which configures a product project. Each product is composed of several product modules.
 */
class ProductPlugin implements Plugin<Project> {
    void apply(Project project) {
        project.configure(project) {
            apply plugin: 'base'
            repositories {
                mavenCentral()
            }

            def product = extensions.create("product", ProductDefinition)
            product.distSrcDirs << rootProject.file('src/dist')
            product.distSrcDirs << project.file('src/dist')

            configurations {
                runtime
            }
            def dist = tasks.register('dist', Zip)
            artifacts {
                archives dist
            }

            afterEvaluate {
                product.modules.each {p ->
                    dependencies { runtime project.project(p.path) }
                }
                archivesBaseName = "some-company-${product.displayName.replaceAll('\\s+', '-').toLowerCase()}"
                dist.configure {
                    into('lib') {
                        from configurations.runtime
                    }
                    from(product.distSrcDirs) {
                        filter(org.apache.tools.ant.filters.ReplaceTokens, tokens: [
                                productName: product.displayName,
                                version: archiveVersion.get()
                        ])
                    }
                }
            }
        }
    }
}
