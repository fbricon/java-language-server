/*******************************************************************************
 * Copyright (c) 2016-2017 Red Hat Inc. and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     Red Hat Inc. - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ls.core.internal.managers;

import static org.eclipse.jdt.ls.core.internal.ProjectUtils.getJavaSourceLevel;
import static org.eclipse.jdt.ls.core.internal.WorkspaceHelper.getProject;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import org.apache.commons.io.FileUtils;
import org.eclipse.buildship.core.BuildConfiguration;
import org.eclipse.buildship.core.FixedVersionGradleDistribution;
import org.eclipse.buildship.core.GradleDistribution;
import org.eclipse.buildship.core.LocalGradleDistribution;
import org.eclipse.buildship.core.WrapperGradleDistribution;
import org.eclipse.buildship.core.internal.CorePlugin;
import org.eclipse.buildship.core.internal.configuration.ProjectConfiguration;
import org.eclipse.core.resources.IFile;
import org.eclipse.core.resources.IProject;
import org.eclipse.core.resources.IWorkspaceRoot;
import org.eclipse.core.resources.ResourcesPlugin;
import org.eclipse.core.runtime.NullProgressMonitor;
import org.eclipse.core.runtime.jobs.Job;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.ls.core.internal.JavaLanguageServerPlugin;
import org.eclipse.jdt.ls.core.internal.ProjectUtils;
import org.eclipse.jdt.ls.core.internal.WorkspaceHelper;
import org.eclipse.jdt.ls.core.internal.managers.ProjectsManager.CHANGE_TYPE;
import org.eclipse.jdt.ls.core.internal.preferences.Preferences.FeatureStatus;
import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.runners.MockitoJUnitRunner;

import com.google.common.collect.ImmutableList;
import com.google.common.io.Files;

/**
 * @author Fred Bricon
 *
 */
@RunWith(MockitoJUnitRunner.class)
public class GradleProjectImporterTest extends AbstractGradleBasedTest{

	private static final String GRADLE1_PATTERN = "**/gradle1";

	@Test
	public void importSimpleGradleProject() throws Exception {
		importSimpleJavaProject();
		assertTaskCompleted(GradleProjectImporter.IMPORTING_GRADLE_PROJECTS);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.jdt.ls.core.internal.managers.AbstractProjectsManagerBasedTest#cleanUp()
	 */
	@Override
	@After
	public void cleanUp() throws Exception {
		super.cleanUp();
		Job.getJobManager().join(CorePlugin.GRADLE_JOB_FAMILY, new NullProgressMonitor());
	}

	@Test
	public void importNestedGradleProject() throws Exception {
		List<IProject> projects = importProjects("gradle/nested");
		assertEquals(3, projects.size());//default + 2 gradle projects
		IProject gradle1 = WorkspaceHelper.getProject("gradle1");
		assertIsGradleProject(gradle1);
		IProject gradle2 = WorkspaceHelper.getProject("gradle2");
		assertIsGradleProject(gradle2);
	}

	@Test
	public void testDeleteInvalidProjects() throws Exception {
		List<IProject> projects = importProjects(Arrays.asList("gradle/nested/gradle1", "gradle/nested/gradle2"));
		assertEquals(3, projects.size());//default + 2 gradle projects
		IProject gradle1 = WorkspaceHelper.getProject("gradle1");
		assertIsGradleProject(gradle1);
		IProject gradle2 = WorkspaceHelper.getProject("gradle2");
		assertIsGradleProject(gradle2);

		projects = importProjects("gradle/nested/gradle1");
		assertEquals(2, projects.size());
		gradle1 = WorkspaceHelper.getProject("gradle1");
		assertNotNull(gradle1);
		gradle2 = WorkspaceHelper.getProject("gradle2");
		assertNull(gradle2);
	}

	@Test
	public void testJavaImportExclusions() throws Exception {
		List<String> javaImportExclusions = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getJavaImportExclusions();
		try {
			javaImportExclusions.add(GRADLE1_PATTERN);
			List<IProject> projects = importProjects("gradle/nested");
			assertEquals(2, projects.size());//default + 1 gradle projects
			IProject gradle1 = WorkspaceHelper.getProject("gradle1");
			assertNull(gradle1);
			IProject gradle2 = WorkspaceHelper.getProject("gradle2");
			assertIsGradleProject(gradle2);
		} finally {
			javaImportExclusions.remove(GRADLE1_PATTERN);
		}
	}

	@Test
	public void testDisableGradleWrapper() throws Exception {
		boolean enabled = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isGradleWrapperEnabled();
		String gradleVersion = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGradleVersion();
		File file = new File(getSourceProjectDirectory(), "gradle/simple-gradle");
		assertTrue(file.isDirectory());
		try {
			GradleDistribution distribution = GradleProjectImporter.getGradleDistribution(file.toPath());
			assertTrue(distribution instanceof WrapperGradleDistribution);
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleWrapperEnabled(false);
			distribution = GradleProjectImporter.getGradleDistribution(file.toPath());
			if (GradleProjectImporter.getGradleHomeFile() != null) {
				assertEquals(distribution.getClass(), LocalGradleDistribution.class);
			} else {
				assertSame(distribution, GradleProjectImporter.DEFAULT_DISTRIBUTION);
			}
			String requiredVersion = "5.2.1";
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleVersion(requiredVersion);
			distribution = GradleProjectImporter.getGradleDistribution(file.toPath());
			assertEquals(distribution.getClass(), FixedVersionGradleDistribution.class);
			assertEquals(((FixedVersionGradleDistribution) distribution).getVersion(), requiredVersion);
			List<IProject> projects = importProjects("eclipse/eclipsegradle");
			assertEquals(2, projects.size());//default + 1 eclipse projects
			IProject eclipse = WorkspaceHelper.getProject("eclipsegradle");
			assertNotNull(eclipse);
			assertTrue(eclipse.getName() + " does not have the Gradle nature", ProjectUtils.isGradleProject(eclipse));
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleWrapperEnabled(enabled);
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleVersion(gradleVersion);
		}
	}

	@Test
	public void testGradleUserHome() throws Exception {
		String gradleUserHomePreference = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGradleUserHome();
		File gradleUserHome = null;
		try {
			gradleUserHome = Files.createTempDir();
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleUserHome(gradleUserHome.getAbsolutePath());
			List<IProject> projects = importProjects("gradle/simple-gradle");
			assertEquals(2, projects.size());//default + 1 eclipse projects
			IProject project = WorkspaceHelper.getProject("simple-gradle");
			assertNotNull(project);
			assertTrue(project.getName() + " does not have the Gradle nature", ProjectUtils.isGradleProject(project));
			assertTrue(gradleUserHome.exists());
			ProjectConfiguration projectConfiguration = CorePlugin.configurationManager().loadProjectConfiguration(project);
			assertEquals(gradleUserHome, projectConfiguration.getBuildConfiguration().getGradleUserHome());
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleUserHome(gradleUserHomePreference);
			if (gradleUserHome != null) {
				FileUtils.deleteDirectory(gradleUserHome);
			}
		}
	}

	@Test
	public void testDisableImportGradle() throws Exception {
		boolean enabled = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isImportGradleEnabled();
		try {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setImportGradleEnabled(false);
			List<IProject> projects = importProjects("eclipse/eclipsegradle");
			assertEquals(2, projects.size());//default + 1 eclipse projects
			IProject eclipse = WorkspaceHelper.getProject("eclipse");
			assertNotNull(eclipse);
			assertFalse(eclipse.getName() + " has the Gradle nature", ProjectUtils.isGradleProject(eclipse));
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setImportGradleEnabled(enabled);
		}
	}

	@Test
	public void testGradlePersistence() throws Exception {
		importProjects("gradle/nested");
		List<IProject> projects = ProjectUtils.getGradleProjects();
		for (IProject project : projects) {
			assertTrue(GradleProjectImporter.shouldSynchronize(project.getLocation().toFile()));
		}
		Job.getJobManager().join(CorePlugin.GRADLE_JOB_FAMILY, new NullProgressMonitor());
		GradleBuildSupport.saveModels();
		for (IProject project : projects) {
			assertFalse(GradleProjectImporter.shouldSynchronize(project.getLocation().toFile()));
		}
		IProject project = WorkspaceHelper.getProject("gradle1");
		File gradleBuild = new File(project.getLocation().toFile(), "build.gradle");
		gradleBuild.setLastModified(System.currentTimeMillis() + 1000);
		assertTrue(GradleProjectImporter.shouldSynchronize(project.getLocation().toFile()));
	}

	@Test
	public void testWorkspaceSettings() throws Exception {
		Map<String, String> env = new HashMap<>();
		Properties sysprops = new Properties();
		File file = null;
		try {
			IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			File rootFile = root.getLocation().toFile();
			file = new File(rootFile, "fakeGradleHome");
			sysprops.setProperty(GradleProjectImporter.GRADLE_HOME, file.getAbsolutePath());
			boolean overrideWorkspaceSettings = GradleProjectImporter.getGradleHomeFile(env, sysprops) != null;
			assertFalse(overrideWorkspaceSettings);
			file.mkdir();
			file.deleteOnExit();
			overrideWorkspaceSettings = GradleProjectImporter.getGradleHomeFile(env, sysprops) != null;
			assertTrue(overrideWorkspaceSettings);
		} finally {
			if (file != null) {
				file.delete();
			}
		}
	}

	@Test
	public void testGradleHome() {
		Map<String, String> env = new HashMap<>();
		Properties sysprops = new Properties();
		File file = null;
		File projectFile = null;
		try {
			IWorkspaceRoot root = ResourcesPlugin.getWorkspace().getRoot();
			File rootFile = root.getLocation().toFile();
			file = new File(rootFile, "fakeGradleHome");
			sysprops.setProperty(GradleProjectImporter.GRADLE_HOME, file.getAbsolutePath());
			boolean overrideWorkspaceSettings = GradleProjectImporter.getGradleHomeFile(env, sysprops) != null;
			assertFalse(overrideWorkspaceSettings);
			file.mkdir();
			file.deleteOnExit();
			overrideWorkspaceSettings = GradleProjectImporter.getGradleHomeFile(env, sysprops) != null;
			assertTrue(overrideWorkspaceSettings);
			projectFile = new File(rootFile, "fakeProject");
			projectFile.mkdir();
			projectFile.deleteOnExit();
			BuildConfiguration build = GradleProjectImporter.getBuildConfiguration(file.toPath());
			assertFalse(build.getGradleUserHome().isPresent());
		} finally {
			if (file != null) {
				file.delete();
			}
			if (projectFile != null) {
				projectFile.delete();
			}
		}
	}

	@Test
	public void testBuildFile() throws Exception {
		IProject project = importSimpleJavaProject();
		IFile file = project.getFile("/target-default/build.gradle");
		assertFalse(projectsManager.isBuildFile(file));
		importProjects("gradle/gradle-withoutjava");
		project = getProject("gradle-withoutjava");
		file = project.getFile("/build.gradle");
		assertTrue(projectsManager.isBuildFile(file));
	}

	@Test
	public void testGradlePropertiesFile() throws Exception {
		IProject project = importSimpleJavaProject();
		IFile file = project.getFile("/target-default/gradle.properties");
		assertFalse(projectsManager.isBuildFile(file));
		importProjects("gradle/gradle-withoutjava");
		project = getProject("gradle-withoutjava");
		file = project.getFile("/gradle.properties");
		assertTrue(projectsManager.isBuildFile(file));
	}

	@Test
	public void testGradleHomePreference() {
		String home = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGradleHome();
		Map<String, String> env = new HashMap<>();
		Properties sysprops = new Properties();
		try {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleHome(null);
			assertNull(GradleProjectImporter.getGradleHomeFile(env, sysprops));

			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleHome("/gradle/home");
			assertEquals(new File("/gradle/home"), GradleProjectImporter.getGradleHomeFile(env, sysprops));
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleHome(home);
		}
	}

	@Test
	public void testGradleArguments() {
		List<String> arguments = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGradleArguments();
		try {
			Path rootPath = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile().toPath();
			BuildConfiguration build = GradleProjectImporter.getBuildConfiguration(rootPath);
			assertTrue(build.getArguments().isEmpty());

			JavaLanguageServerPlugin.getPreferencesManager().getPreferences()
					.setGradleArguments(ImmutableList.of("-Pproperty=value", "--stacktrace"));
			build = GradleProjectImporter.getBuildConfiguration(rootPath);
			assertEquals(2, build.getArguments().size());
			assertTrue(build.getArguments().contains("-Pproperty=value"));
			assertTrue(build.getArguments().contains("--stacktrace"));
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleArguments(arguments);
		}
	}

	@Test
	public void testGradleOfflineMode() {
		boolean offlineMode = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().isImportGradleOfflineEnabled();
		try {
			Path rootPath = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile().toPath();
			BuildConfiguration build = GradleProjectImporter.getBuildConfiguration(rootPath);
			assertFalse(build.isOfflineMode());
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setImportGradleOfflineEnabled(true);
			build = GradleProjectImporter.getBuildConfiguration(rootPath);
			assertTrue(build.isOfflineMode());
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setImportGradleOfflineEnabled(offlineMode);
		}
	}

	@Test
	public void testGradleJvmArguments() {
		List<String> jvmArguments = JavaLanguageServerPlugin.getPreferencesManager().getPreferences().getGradleJvmArguments();
		try {
			Path rootPath = ResourcesPlugin.getWorkspace().getRoot().getLocation().toFile().toPath();
			BuildConfiguration build = GradleProjectImporter.getBuildConfiguration(rootPath);
			assertTrue(build.getJvmArguments().isEmpty());

			JavaLanguageServerPlugin.getPreferencesManager().getPreferences()
					.setGradleJvmArguments(ImmutableList.of("-Djavax.net.ssl.trustStore=truststore.jks"));
			build = GradleProjectImporter.getBuildConfiguration(rootPath);
			assertEquals(1, build.getJvmArguments().size());
			assertTrue(build.getJvmArguments().contains("-Djavax.net.ssl.trustStore=truststore.jks"));
		} finally {
			JavaLanguageServerPlugin.getPreferencesManager().getPreferences().setGradleJvmArguments(jvmArguments);
		}
	}

	@Test
	public void testDeleteClasspath() throws Exception {
		FeatureStatus status = preferenceManager.getPreferences().getUpdateBuildConfigurationStatus();
		try {
			preferenceManager.getPreferences().setUpdateBuildConfigurationStatus(FeatureStatus.automatic);
			IProject project = importSimpleJavaProject();
			assertIsJavaProject(project);
			assertIsGradleProject(project);
			IFile dotClasspath = project.getFile(IJavaProject.CLASSPATH_FILE_NAME);
			File file = dotClasspath.getRawLocation().toFile();
			assertTrue(file.exists());
			file.delete();
			projectsManager.fileChanged(file.toPath().toUri().toString(), CHANGE_TYPE.DELETED);
			waitForBackgroundJobs();
			Job.getJobManager().join(CorePlugin.GRADLE_JOB_FAMILY, new NullProgressMonitor());
			project = getProject("simple-gradle");
			assertIsGradleProject(project);
			assertIsJavaProject(project);
			IFile bin = project.getFile("bin");
			assertFalse(bin.getRawLocation().toFile().exists());
			assertTrue(dotClasspath.exists());
		} finally {
			preferenceManager.getPreferences().setUpdateBuildConfigurationStatus(status);
		}
	}

	@Test
	public void testJava11Project() throws Exception {
		IProject project = importGradleProject("gradle-11");
		assertIsJavaProject(project);
		assertEquals("11", getJavaSourceLevel(project));
		assertNoErrors(project);
	}

	@Test
	public void testJava12Project() throws Exception {
		testJavaProjectWithPreviewFeatures("12", false, JavaCore.WARNING);
	}

	@Test
	public void testJava13Project() throws Exception {
		testJavaProjectWithPreviewFeatures("13", true /* The project has enabled preview features in the jdt setting*/, JavaCore.IGNORE);
	}

	@Test
	public void testJava14Project() throws Exception {
		testJavaProjectWithPreviewFeatures("14", true /* The project has enabled preview features in the jdt setting*/, JavaCore.IGNORE);
	}

	private void testJavaProjectWithPreviewFeatures(String javaVersion, boolean enabled, String severity) throws Exception {
		IProject project = importGradleProject("gradle-" + javaVersion);
		assertIsJavaProject(project);
		assertEquals(javaVersion, getJavaSourceLevel(project));
		IJavaProject javaProject = JavaCore.create(project);
		assertEquals((enabled) ? JavaCore.ENABLED : JavaCore.DISABLED, javaProject.getOption(JavaCore.COMPILER_PB_ENABLE_PREVIEW_FEATURES, true));
		assertEquals(severity, javaProject.getOption(JavaCore.COMPILER_PB_REPORT_PREVIEW_FEATURES, true));
	}
}
