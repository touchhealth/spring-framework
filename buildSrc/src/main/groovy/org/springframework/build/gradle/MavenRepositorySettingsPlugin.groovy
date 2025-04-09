/*
 * Copyright 2002-2025 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.build.gradle

import org.apache.maven.settings.Server
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest
import org.gradle.api.GradleException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.sonatype.plexus.components.cipher.DefaultPlexusCipher
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher

/**
 * Plugin that adds an extension with the settings of the Maven repository used in the {@code uploadArchives} tasks.
 * <p>
 * The following Gradle properties are required:
 * <ul>
 * <li>maven.serverId</li>
 * <li>maven.snapshotsRepositoryUrl</li>
 * <li>maven.releasesRepositoryUrl</li>
 * </ul>
 * The repository URL (snapshots or releases) is chosen based on the value of the {@code version} Gradle property.
 * <p>
 * The username and password are read from the {@code ~/.m2/settings.xml} file. The value of the {@code maven.serverId}
 * property must specify the ID of a server in this XML file. The settings object will be populated with the username
 * and password of this server.
 */
class MavenRepositorySettingsPlugin implements Plugin<Project> {

	private static final PROP_SERVER_ID = "maven.serverId"
	private static final PROP_SNAPSHOTS_REPOSITORY_URL = "maven.snapshotsRepositoryUrl"
	private static final PROP_RELEASES_REPOSITORY_URL = "maven.releasesRepositoryUrl"

	private static final M2_DIR = System.getProperty("user.home") + "/.m2"
	private static final SETTINGS_FILE = M2_DIR + "/settings.xml"
	private static final SETTINGS_SECURITY_FILE = M2_DIR + "/settings-security.xml"

	void apply(Project project) {
		def server = getServer(project)

		project.extensions.add("mavenRepository", new MavenRepositorySettings(
				url: getRepositoryUrl(project),
				username: server.username,
				password: decrypt(server.password),
		))
	}

	private static Server getServer(Project project) {
		def id = project.property(PROP_SERVER_ID)

		def request = new DefaultSettingsBuildingRequest()
		request.userSettingsFile = new File(SETTINGS_FILE)
		request.systemProperties = System.getProperties()

		def settingsBuilder = new DefaultSettingsBuilderFactory().newInstance()
		def settings = settingsBuilder.build(request).getEffectiveSettings()
		def server = settings.servers.find { it.id == id }

		if (server == null) {
			throw new GradleException("Could not find a server with ID ${id} in the file ${SETTINGS_FILE}")
		}
		return server
	}

	private static String getRepositoryUrl(Project project) {
		def isSnapshotVersion = project.version.contains("SNAPSHOT")
		def propertyName = isSnapshotVersion ? PROP_SNAPSHOTS_REPOSITORY_URL : PROP_RELEASES_REPOSITORY_URL
		return project.property(propertyName)
	}

	private static String decrypt(String password) {
		def cipher = new DefaultPlexusCipher()
		def dispatcher = new DefaultSecDispatcher(cipher, null, SETTINGS_SECURITY_FILE)
		return dispatcher.decrypt(password)
	}

	static class MavenRepositorySettings {

		String url
		String username
		String password
	}
}
