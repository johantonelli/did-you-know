plugins {
    kotlin("js") version "1.9.22"
}

group = "com.wikifacts"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

kotlin {
    js {
        browser {
            commonWebpackConfig {
                cssSupport {
                    enabled.set(true)
                }
            }
            webpackTask {
                mainOutputFileName.set("wikifacts.js")
            }
            testTask {
                useKarma {
                    useChromeHeadless()
                }
            }
        }
        binaries.executable()
    }
}

dependencies {
    testImplementation(kotlin("test"))
}
