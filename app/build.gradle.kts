plugins {
	id("com.android.application")
	id("org.jetbrains.kotlin.plugin.compose")
}

val categorizationEndpoint = providers.gradleProperty("BLOCKY_CATEGORIZATION_ENDPOINT")
	.orElse(providers.environmentVariable("BLOCKY_CATEGORIZATION_ENDPOINT"))
	.getOrElse("")
val escapedCategorizationEndpoint = categorizationEndpoint
	.replace("\\", "\\\\")
	.replace("\"", "\\\"")
val quotedCategorizationEndpoint = "\"" + escapedCategorizationEndpoint + "\""

android {
	namespace = "com.ziacik.blocky"
	compileSdk = 37

	defaultConfig {
		applicationId = "com.ziacik.blocky"
		minSdk = 26
		targetSdk = 36
		versionCode = 1
		versionName = "0.1.0"

		testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
		buildConfigField("String", "CATEGORIZATION_ENDPOINT", quotedCategorizationEndpoint)
	}

	buildFeatures {
		compose = true
		buildConfig = true
	}

	compileOptions {
		sourceCompatibility = JavaVersion.VERSION_17
		targetCompatibility = JavaVersion.VERSION_17
	}
}

dependencies {
	val composeBom = platform("androidx.compose:compose-bom:2026.09.00")
	implementation(composeBom)
	androidTestImplementation(composeBom)

	implementation("androidx.activity:activity-compose:1.13.0")
	implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.11.0")
	implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.11.0")
	implementation("androidx.lifecycle:lifecycle-viewmodel-ktx:2.11.0")
	implementation("androidx.work:work-runtime-ktx:2.11.2")
	implementation("androidx.compose.material3:material3")
	implementation("androidx.compose.material:material-icons-extended")
	implementation("androidx.compose.ui:ui")
	implementation("androidx.compose.ui:ui-tooling-preview")
	debugImplementation("androidx.compose.ui:ui-tooling")

	implementation("com.google.android.gms:play-services-code-scanner:16.1.0")

	testImplementation("junit:junit:4.13.2")
	testImplementation("org.json:json:20260814")
	testImplementation("org.robolectric:robolectric:4.17")
}
