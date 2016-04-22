package ru.yandex.jenkins.plugins.compound;

import java.io.IOException;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;

/**
 * a `NodeProperty` that would contribute this node's environment
 * 
 * @author pupssman
 */
public class CompoundNodeProperty extends NodeProperty<CompoundSlave> {
	public CompoundNodeProperty(CompoundSlave compoundSlave) {
		super();
		
		setNode(compoundSlave);
	}

	@Override
	public void buildEnvVars(EnvVars env, TaskListener listener) throws IOException, InterruptedException {
		try {
			CompoundEnvironmentContributor.buildEnvironmentFor(node, env, listener);
		} catch (CompoundingException e) {
			throw new IOException(e);
		}
	}
	
	@Extension
	public static class DescriptorImpl extends NodePropertyDescriptor {
		@Override
		public String getDisplayName() {
			return "Compoundy Stuff";
		}
	}
}
