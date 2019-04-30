// tag::apply-plugin[]
plugins {
    `xcode`
}
// end::apply-plugin[]

// tag::configure-workspace-location[]
xcode {
    workspace.location.set(file("workspace.xcworkspace"))
}
// end::configure-workspace-location[]