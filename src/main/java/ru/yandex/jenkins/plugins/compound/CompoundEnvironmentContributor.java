package ru.yandex.jenkins.plugins.compound;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.TaskListener;
import hudson.model.Computer;
import hudson.model.EnvironmentContributor;
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.Run;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;
import hudson.util.DescribableList;

import java.io.IOException;

import org.netbeans.insane.impl.InsaneEngine;


/**
 * This thing is used to contribute slave parameters to running build
 *
 * @author pupssman
 */
@Extension
public class CompoundEnvironmentContributor extends EnvironmentContributor {
	@Override
	public void buildEnvironmentFor(@SuppressWarnings("rawtypes") Run run, EnvVars envs, TaskListener listener) throws IOException, InterruptedException {
		Executor executor = run.getExecutor();
		if (executor == null) {
			return;
		}

		Computer owner = executor.getOwner();
		// this may be a bit pointless, but still
		if (owner == null) {
			return;
		}

		Node node = owner.getNode();
		if (node == null) {
			return;
		}

		if (!(node instanceof CompoundSlave)) {
			return;
		}

		DescribableList<NodeProperty<?>, NodePropertyDescriptor> props = node.getNodeProperties();

		// previous version of the plugin does not have this fancy node property
		// grant old slaves such property
		if (props.get(CompoundNodeProperty.class) == null) {
			props.add(new CompoundNodeProperty((CompoundSlave) node));
		}

		props.get(CompoundNodeProperty.class).buildEnvVars(envs, listener);
	}
}
