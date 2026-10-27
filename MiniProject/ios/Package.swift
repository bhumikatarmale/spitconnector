// swift-tools-version: 5.9
// The swift-tools-version declares the minimum version of Swift required to build this package.

import PackageDescription

let package = Package(
    name: "CampusMesh",
    platforms: [
        .iOS(.v15),
        .macOS(.v12)
    ],
    products: [
        .library(
            name: "CampusMesh",
            targets: ["CampusMesh"]
        ),
    ],
    targets: [
        .target(
            name: "CampusMesh",
            path: "CampusMesh"
        ),
    ]
)
