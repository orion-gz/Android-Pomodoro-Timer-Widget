plugins {
    alias(libs.plugins.android.library)
    id("maven-publish")
}

android {
    namespace = "orion.gz.pomodorotimer"
    compileSdk = 36

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation(libs.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.ext.junit)
    androidTestImplementation(libs.espresso.core)
}

group = "com.github.orion-gz"
version = "1.0.7"

publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate {
                from(components["release"])
            }

            groupId = project.group.toString()
            artifactId = "Android-Pomodoro-Timer-Widget"
            version = project.version.toString()

            pom {
                name.set("PomodoroTimer")
                description.set("This is an android Pomodoro Timer custom view widget with java")
                url.set("https://github.com/orion-gz/Android-Pomodoro-Timer-Widget")

                developers {
                    developer {
                        id.set("orion-gz")
                        name.set("JungWoo Lee")
                        email.set("asdfg60842@gmail.com")
                    }
                }

                scm {
                    connection.set("scm:git:git://github.com/orion-gz/Pomodoro-Timer-Widget.git")
                    developerConnection.set("scm:git:ssh://github.com/orion-gz/Android-Pomodoro-Timer-Widget.git")
                    url.set("https://github.com/orion-gz/Android-Pomodoro-Timer-Widget/")
                }
            }
        }
    }
}