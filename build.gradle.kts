import java.io.ByteArrayOutputStream

val version = "2.12.1"
val suffix = "SNAPSHOT"

// Strings embedded into the build.
var gitRevision by extra("")
var apktoolVersion by extra("")

defaultTasks("build", "shadowJar", "proguard")

// Functions
val gitDescribe: String? by lazy {
    val stdout = ByteArrayOutputStream()
    try {
        rootProject.exec {
            commandLine("git", "describe", "--tags")
            standardOutput = stdout
        }
        stdout.toString().trim().replace("-g", "-")
    } catch (e: Exception) {
        null
    }
}

val gitBranch: String? by lazy {
    val stdout = ByteArrayOutputStream()
    try {
        rootProject.exec {
            commandLine("git", "rev-parse", "--abbrev-ref", "HEAD")
            standardOutput = stdout
        }
        stdout.toString().trim()
    } catch (e: Exception) {
        null
    }
}

if ("release" !in gradle.startParameter.taskNames) {
    val hash = this.gitDescribe

    if (hash == null) {
        gitRevision = "dirty"
        apktoolVersion = "$version-dirty"
        project.logger.lifecycle("Building SNAPSHOT (no .git folder found)")
    } else {
        gitRevision = hash
        apktoolVersion = "$hash-SNAPSHOT"
        project.logger.lifecycle("Building SNAPSHOT ($gitBranch): $gitRevision")
    }
} else {
    gitRevision = ""
    apktoolVersion = if (suffix.isNotEmpty()) "$version-$suffix" else version;
    project.logger.lifecycle("Building RELEASE ($gitBranch): $apktoolVersion")
}

plugins {
    `java-library`
    `maven-publish`
    signing
}

tasks.withType<JavaCompile> {
    options.compilerArgs.add("-Xlint:-options")
    options.compilerArgs.add("--release 8")

    options.encoding = "UTF-8"
}

allprojects {
    repositories {
        mavenCentral()
        google()
    }
}

subprojects {
    apply(plugin = "java")
    apply(plugin = "java-library")

    java {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    val mavenProjects = arrayOf(
        "brut.j.common", "brut.j.util", "brut.j.dir", "brut.j.xml", "brut.j.yaml",
        "apktool-lib", "apktool-cli"
    )

    if (project.name in mavenProjects) {
        apply(plugin = "maven-publish")
        apply(plugin = "signing")

        java {
            withJavadocJar()
            withSourcesJar()
        }

        publishing {
            repositories {
                maven {
                    url = uri("https://maven.pkg.github.com/zjns/Apktool")
                    credentials {
                        username = System.getenv("GITHUB_ACTOR") ?: project.findProperty("gpr.user").toString()
                        password = System.getenv("GITHUB_TOKEN") ?: project.findProperty("gpr.key").toString()
                    }
                }
            }
            publications {
                register("mavenJava", MavenPublication::class) {
                    from(components["java"])
                    groupId = "kofua.app.revanced"
                    artifactId = project.name
                    version = apktoolVersion

                    pom {
                        name = "Apktool"
                        description = "A tool for reverse engineering Android apk files."
                        url = "https://apktool.org"

                        licenses {
                            license {
                                name = "The Apache License 2.0"
                                url = "https://opensource.org/licenses/Apache-2.0"
                            }
                        }
                        developers {
                            developer {
                                id = "iBotPeaches"
                                name = "Connor Tumbleson"
                                email = "connor.tumbleson@gmail.com"
                            }
                            developer {
                                id = "brutall"
                                name = "Ryszard Wiśniewski"
                                email = "brut.alll@gmail.com"
                            }
                        }
                        scm {
                            connection = "scm:git:git://github.com/zjns/Apktool.git"
                            developerConnection = "scm:git:git@github.com:zjns/Apktool.git"
                            url = "https://github.com/zjns/Apktool"
                        }
                    }
                }
            }
        }

        tasks.withType<Javadoc>() {
            (options as StandardJavadocDocletOptions).addStringOption("Xdoclint:none", "-quiet")
        }

        signing {
            sign(publishing.publications["mavenJava"])
        }
    }
}

task("release") {
  // Used for official releases.
}

tasks.wrapper {
    distributionType = Wrapper.DistributionType.ALL
}
