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

package org.springframework.build.maven;


import org.apache.maven.settings.Server;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.building.DefaultSettingsBuilderFactory;
import org.apache.maven.settings.building.DefaultSettingsBuildingRequest;
import org.apache.maven.settings.building.SettingsBuilder;
import org.apache.maven.settings.building.SettingsBuildingException;
import org.apache.maven.settings.building.SettingsBuildingRequest;
import org.gradle.api.GradleException;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.sonatype.plexus.components.cipher.DefaultPlexusCipher;
import org.sonatype.plexus.components.cipher.PlexusCipher;
import org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcher;
import org.sonatype.plexus.components.sec.dispatcher.SecDispatcherException;

import javax.annotation.Nonnull;
import java.io.File;


/**
 * Plugin that adds an extension with the settings of the Maven repository used by the maven-publish plugin.
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
public class MavenRepositorySettingsPlugin implements Plugin<Project> {

	private static final String PROP_SERVER_ID = "maven.serverId";
	private static final String PROP_SNAPSHOTS_REPOSITORY_URL = "maven.snapshotsRepositoryUrl";
	private static final String PROP_RELEASES_REPOSITORY_URL = "maven.releasesRepositoryUrl";

	private static final String M2_DIR = System.getProperty("user.home") + "/.m2";
	private static final String SETTINGS_FILE = M2_DIR + "/settings.xml";
	private static final String SETTINGS_SECURITY_FILE = M2_DIR + "/settings-security.xml";

	@Override
	public void apply(@Nonnull Project project) {
		try {
			doApply(project);
		} catch (SecDispatcherException | SettingsBuildingException e) {
			throw new GradleException("Failed to load Maven repository settings", e);
		}
	}

	private void doApply(Project project) throws SecDispatcherException, SettingsBuildingException {
		Server server = getServer(project);

		project.getExtensions().add("mavenRepository", new MavenRepositorySettings(
				getRepositoryUrl(project), server.getUsername(), decrypt(server.getPassword())
		));
	}

	private static Server getServer(Project project) throws SettingsBuildingException {
		String id = (String) project.property(PROP_SERVER_ID);

		SettingsBuildingRequest request = new DefaultSettingsBuildingRequest();
		request.setUserSettingsFile(new File(SETTINGS_FILE));
		request.setSystemProperties(System.getProperties());

		SettingsBuilder settingsBuilder = new DefaultSettingsBuilderFactory().newInstance();
		Settings settings = settingsBuilder.build(request).getEffectiveSettings();
		Server server = settings.getServer(id);

		if (server == null) {
			String message = String.format("Could not find a server with ID %s in the file %s", id, SETTINGS_FILE);
			throw new GradleException(message);
		}
		return server;
	}

	private static String getRepositoryUrl(Project project) {
		boolean isSnapshotVersion = ((String) project.getVersion()).contains("SNAPSHOT");
		String propertyName = isSnapshotVersion ? PROP_SNAPSHOTS_REPOSITORY_URL : PROP_RELEASES_REPOSITORY_URL;
		return (String) project.property(propertyName);
	}

	private static String decrypt(String password) throws SecDispatcherException {
		PlexusCipher cipher = new DefaultPlexusCipher();
		SecDispatcher dispatcher = new DefaultSecDispatcher(cipher, null, SETTINGS_SECURITY_FILE);
		return dispatcher.decrypt(password);
	}

	public static class MavenRepositorySettings {

		private final String url;
		private final String username;
		private final String password;

		private MavenRepositorySettings(String url, String username, String password) {
			this.url = url;
			this.username = username;
			this.password = password;
		}

		public String getUrl() {
			return url;
		}

		public String getUsername() {
			return username;
		}

		public String getPassword() {
			return password;
		}
	}
}
