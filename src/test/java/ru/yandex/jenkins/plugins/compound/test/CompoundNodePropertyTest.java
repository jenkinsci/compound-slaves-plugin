package ru.yandex.jenkins.plugins.compound.test;

import static org.junit.Assert.*;

import java.util.Arrays;

import jedi.assertion.Assert;
import hudson.EnvVars;
import hudson.slaves.DumbSlave;
import hudson.util.StreamTaskListener;

import org.junit.Rule;
import org.junit.Test;
import org.jvnet.hudson.test.JenkinsRule;

import ru.yandex.jenkins.plugins.compound.CompoundNodeProperty;
import ru.yandex.jenkins.plugins.compound.CompoundSlave;
import ru.yandex.jenkins.plugins.compound.CompoundSlave.Entry;

public class CompoundNodePropertyTest {
	
	@Rule
	public JenkinsRule j = new JenkinsRule();
	@Test
	public void testNPResolution() throws Exception {
		DumbSlave slave = j.createOnlineSlave();
		
		CompoundSlave compoundSlave = new CompoundSlave("Compound", "Test", "Test", Arrays.asList(new Entry(slave.getDisplayName(), "ROOT")));
		
		EnvVars env = new EnvVars();
		
		compoundSlave.getNodeProperties().get(CompoundNodeProperty.class).buildEnvVars(env, StreamTaskListener.fromStdout());
		
		assertNotNull(env.get("root_1_ip"));
	}
}
