import XCTest
@testable import App

class AppTests: XCTestCase {
    func testEqualsExpectedMessage() {
        XCTAssertEqual("Hello, World!", getMessage())
    }
}
