package ru.yandex.jenkins.plugins.compound.test;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasKey;
import static org.hamcrest.Matchers.not;
import static org.junit.Assert.assertThat;
import hudson.EnvVars;
import hudson.model.FreeStyleBuild;
import hudson.model.FreeStyleProject;
import hudson.tasks.Shell;
import hudson.util.StreamTaskListener;

import java.util.Arrays;

import org.apache.commons.io.FileUtils;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.jvnet.hudson.test.JenkinsRule;

import ru.yandex.jenkins.plugins.compound.CompoundNodeProperty;
import ru.yandex.jenkins.plugins.compound.CompoundSlave;
import ru.yandex.jenkins.plugins.compound.CompoundSlave.Entry;

public class CompoundNodePropertyTest {

	@Rule public JenkinsRule j = new JenkinsRule();
	@Rule public TestName name = new TestName();

	@Test
	public void testNPResolution() throws Exception {
		CompoundSlave compoundSlave = createOnlineCompound(name.getMethodName());

		EnvVars env = compoundSlave.getComputer().buildEnvironment(StreamTaskListener.fromStdout());

		assertThat(env, hasKey("root_1_ip"));
	}

	@Test
	public void testReloadDurability() throws Exception {
		createOnlineCompound(name.getMethodName());

		j.jenkins.reload();

		EnvVars env = j.jenkins.getComputer(name.getMethodName()).buildEnvironment(StreamTaskListener.fromStdout());

		assertThat(env, hasKey("root_1_ip"));
	}

	@Test
	public void testConfigRoundtrip() throws Exception {
		CompoundSlave compoundSlave = createOnlineCompound(name.getMethodName());

		j.submit(j.createWebClient().getPage(compoundSlave, "configure").getFormByName("config"));

		EnvVars env = j.jenkins.getComputer(name.getMethodName()).buildEnvironment(StreamTaskListener.fromStdout());

		assertThat(env, hasKey("root_1_ip"));
	}

	@Test
	public void testConfigUpdate() throws Exception {
		CompoundSlave compoundSlave = createOnlineCompound(name.getMethodName());

		CompoundNodeProperty before = compoundSlave.getNodeProperties().get(CompoundNodeProperty.class);

		j.submit(j.createWebClient().getPage(compoundSlave, "configure").getFormByName("config"));

		CompoundNodeProperty after = j.jenkins.getNode(name.getMethodName()).getNodeProperties().get(CompoundNodeProperty.class);

		assertThat(after, not(before));
	}

	@Test
	public void testJobEnv() throws Exception {
		CompoundSlave compoundSlave = createOnlineCompound(name.getMethodName());

		FreeStyleProject project = j.createFreeStyleProject();
		project.setAssignedNode(compoundSlave);
		project.getBuildersList().add(new Shell("env"));
		FreeStyleBuild build = project.scheduleBuild2(0).get();

		assertThat(FileUtils.readFileToString(build.getLogFile()), containsString("root_1_ip"));
	}

	@Test
	public void testJobEnvMigration() throws Exception {
		CompoundSlave compoundSlave = createOnlineCompound(name.getMethodName());

		// old version of the plugin does not have that
		compoundSlave.getNodeProperties().remove(CompoundNodeProperty.class);

		FreeStyleProject project = j.createFreeStyleProject();
		project.setAssignedNode(compoundSlave);
		project.getBuildersList().add(new Shell("env"));
		FreeStyleBuild build = project.scheduleBuild2(0).get();

		assertThat(FileUtils.readFileToString(build.getLogFile()), containsString("root_1_ip"));
	}

	private CompoundSlave createOnlineCompound(String name) throws Exception{
		CompoundSlave compoundSlave = new CompoundSlave(name, "Test", "Test",
				Arrays.asList(new Entry(j.createOnlineSlave().getDisplayName(), CompoundSlave.ROLE_ROOT)));

		j.jenkins.addNode(compoundSlave);

		Thread.sleep(500); // let it launch

		return compoundSlave;
	}
}
