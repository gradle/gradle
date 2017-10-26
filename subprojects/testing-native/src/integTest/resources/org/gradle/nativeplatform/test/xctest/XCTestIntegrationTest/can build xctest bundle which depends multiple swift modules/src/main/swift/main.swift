import Greeter

internal func getMessage() -> String {
    let g = Greeter()
    return g.getMessage()
}

// Simple hello world app
print(getMessage())
