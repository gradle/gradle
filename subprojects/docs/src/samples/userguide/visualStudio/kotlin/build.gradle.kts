// tag::apply-plugin[]
plugins {
    `visual-studio`
}
// end::apply-plugin[]

// tag::configure-solution-location[]
visualStudio {
    solution.location.set(file("solution.sln"))
}
// end::configure-solution-location[]

// tag::configure-project-and-filters-location[]
visualStudio {
    projects.all {
        projectFile.location = file("project.vcxproj")
        filtersFile.location = file("project.vcxproj.filters")
    }
}
// end::configure-project-and-filters-location[]