/*
 * Copyright 2010 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.internal.artifacts.mvnsettings;

import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.settings.DefaultMavenSettingsBuilder;
import org.apache.maven.settings.MavenSettingsBuilder;
import org.apache.maven.settings.Settings;
import org.gradle.api.internal.artifacts.PlexusLoggerAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.lang.reflect.Field;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * @author Steve Ebersole
 */
public class DefaultLocalMavenRepositoryLocator implements LocalMavenRepositoryLocator {
    private static final Logger LOGGER = LoggerFactory.getLogger(DefaultLocalMavenRepositoryLocator.class);
    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\$\\{([^\\}]*)\\}");

    private final MavenFileLocations mavenFileLocations;
    private final Map<String, String> systemProperties;
    private final Map<String, String> environmentVariables;

    public DefaultLocalMavenRepositoryLocator(MavenFileLocations mavenFileLocations, Map<String, String> systemProperties, Map<String, String> environmentVariables) {
        this.mavenFileLocations = mavenFileLocations;
        this.systemProperties = systemProperties;
        this.environmentVariables = environmentVariables;
    }

    public File getLocalMavenRepository() {
        Settings settings = buildSettings();
        String repoPath = settings.getLocalRepository().trim();
        return new File(resolvePlaceholders(repoPath));
    }

    private String resolvePlaceholders(String value) {
        StringBuffer result = new StringBuffer();
        Matcher matcher = PLACEHOLDER_PATTERN.matcher(value);

        while (matcher.find()) {
            String placeholder = matcher.group(1);
            String replacement = placeholder.startsWith("env.") ? environmentVariables.get(placeholder.substring(4)) : systemProperties.get(placeholder);
            if (replacement == null) {
                throw new CannotLocateLocalMavenRepositoryException(String.format("Cannot resolve placeholder '%s' in value '%s'", placeholder, value));
            }
            matcher.appendReplacement(result, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(result);

        return result.toString();
    }

    private Settings buildSettings() {
        try {
            final MavenExecutionRequest request = new DefaultMavenExecutionRequest();
            request.setUserSettingsFile(mavenFileLocations.getUserSettingsFile());
            request.setGlobalSettingsFile(mavenFileLocations.getGlobalSettingsFile());
            return createSettingsBuilder().buildSettings(request);
        } catch (Exception e) {
            throw new CannotLocateLocalMavenRepositoryException(e);
        }
    }

    private MavenSettingsBuilder createSettingsBuilder() throws Exception {
        DefaultMavenSettingsBuilder builder = new DefaultMavenSettingsBuilder();
        builder.enableLogging(new PlexusLoggerAdapter(LOGGER));

        return builder;
    }
}
