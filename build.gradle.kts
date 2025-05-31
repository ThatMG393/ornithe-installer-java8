import java.net.URI

plugins {
	java
	`java-library`
	`maven-publish`
	application

	id("net.kyori.blossom") version "1.3.1"
	id("com.diffplug.spotless") version "6.19.0"
	id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "net.ornithemc"
val env = System.getenv()
version = if (env["SNAPSHOTS_URL"] != null) {
	"0-SNAPSHOT"
} else {
	"0.11.5"
}

base {
	archivesName.set(project.name)
}

repositories {
	mavenCentral()

	maven("https://maven.quiltmc.org/repository/release/") {
		name = "QuiltMC Releases"
	}
}

dependencies {
	implementation("org.quiltmc.parsers:json:0.2.1")

	compileOnly("org.jetbrains:annotations:26.0.2")
}

spotless {
	java {
		// Use comma separator for openjdk like license headers
		licenseHeaderFile(project.file("codeformat/HEADER")).yearSeparator(", ")
	}
}

// Apply constant string constant replacements for the project version in CLI class
blossom {
	replaceToken("__INSTALLER_VERSION", project.version)
}

java {
	sourceCompatibility = JavaVersion.VERSION_1_8;
    targetCompatibility = JavaVersion.VERSION_1_8;
	
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(21))
	}
}

application {
	mainClass.set("org.quiltmc.installer.Main")
}

tasks.jar {
	manifest {
		attributes["Implementation-Title"] = "Ornithe-Installer"
		attributes["Implementation-Version"] = project.version
		attributes["Multi-Release"] = true

		attributes["Main-Class"] = "org.quiltmc.installer.Main"
	}
}

tasks.shadowJar {
	relocate("org.quiltmc.parsers.json", "org.quiltmc.installer.lib.parsers.json")
//	minimize()

	// Compiler does not know which set method we are targeting with null value
	val classifier: String? = null;
	archiveClassifier.set(classifier)
}

tasks.assemble {
	dependsOn(tasks.shadowJar)
}

tasks.named("distTar") {
	dependsOn(tasks.shadowJar)
}

tasks.named("distZip") {
	dependsOn(tasks.shadowJar)
}

distributions {
	main {
		contents {
			from(tasks.shadowJar) {
				into("lib")
			}
			// Exclude the regular jar to avoid duplication
			exclude("**/${project.name}-${project.version}.jar")
		}
	}
}

val copyForNative = tasks.register<Copy>("copyForNative") {
	dependsOn(tasks.shadowJar)
	dependsOn(tasks.jar)
	from(tasks.shadowJar)
	into(file("build"))

	rename {
		return@rename if (it.contains("ornithe-installer")) {
			"native-ornithe-installer.jar"
		} else {
			it
		}
	}
}


publishing {
	publications {
		if (env["TARGET"] == null) {
			create<MavenPublication>("mavenJava") {
				from(components["java"])
			}
		} else {
			// TODO: When we build macOS make this work
			val architecture = env["TARGET"]

			create<MavenPublication>("mavenNatives") {
				groupId = "net.ornithemc.ornithe-installer-native-bootstrap"
				artifactId = "windows-$architecture"

				artifact {
					file("$projectDir/native/target/$architecture-pc-windows-msvc/release/ornithe-installer.exe")
				}
			}
		}
	}

	repositories {
		if (env["MAVEN_URL"] != null) {
			repositories.maven {
				url = URI(env["MAVEN_URL"]!!)

				credentials {
					username = env["MAVEN_USERNAME"]
					password = env["MAVEN_PASSWORD"]
				}
			}
		} else if (env["SNAPSHOTS_URL"] != null) {
			repositories.maven {
				url = URI(env["SNAPSHOTS_URL"]!!)

				credentials {
					username = env["SNAPSHOTS_USERNAME"]
					password = env["SNAPSHOTS_PASSWORD"]
				}
			}
		}
	}
}
