package ru.yandex.jenkins.plugins.compound;

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.InvisibleAction;
import hudson.model.TaskListener;
import hudson.model.Computer;
import hudson.model.EnvironmentContributor;
import hudson.model.Executor;
import hudson.model.Node;
import hudson.model.Run;
import hudson.model.Slave;
import hudson.remoting.Callable;

import java.io.IOException;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.Socket;
import java.text.MessageFormat;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;


/**
 * This thing is used to contribute slave parameters to running build
 *
 * @author pupssman
 */
@Extension
public class CompoundEnvironmentContributor extends EnvironmentContributor {

	private static final int[] TEST_PORTS = new int[] {22, 23, 80, 443, 8080};
	private static final int PING_TIMEOUT = 500;

	public static final class EnvironmentAction extends InvisibleAction {
		private final Map<String, String> values;

		public EnvironmentAction(Map<String, String> values) {
			this.values = values;
		}

		public Map<String, String> getValues() {
			return values;
		}
	}

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

		if (node instanceof CompoundSlave) {
			listener.getLogger().println("[compound-slave] Contributing environment for " + run.getFullDisplayName());

			try {
				buildEnvironmentFor((CompoundSlave) node, envs, listener);
			} catch (CompoundingException e) {
				throw new IOException(e);
			}
		}

	}

	/*package*/ static void buildEnvironmentFor(CompoundSlave slave, EnvVars envs, TaskListener listener) throws CompoundingException {
		EnvironmentAction environmentAction = slave.toComputer().getAction(EnvironmentAction.class);

		Map<String, String> values;

		if (environmentAction != null) {
			values = environmentAction.getValues();
		} else {
			listener.getLogger().println("[compound-slave] No environment known - computing...");
			values = computeValues(slave, listener);
			slave.toComputer().addAction(new EnvironmentAction(values));
		}

		envs.putAll(values);
	}

	/**
	 * Compute actual values based on given {@link CompoundSlave}
	 *
	 * @param slave
	 * @param listener
	 * @return
	 * @throws CompoundingException if there was a problem with contacting sub-slaves
	 */
	private static Map<String, String> computeValues(CompoundSlave slave, TaskListener listener) throws CompoundingException {
		Map<String, String> values;
		values = new HashMap<String, String>();

		for (String role: slave.getAllSlaves().keySet()) {
			int i = 0;
			for (Slave subSlave: slave.getAllSlaves().get(role)) {
				i++;

				String v4_address = null;
				String v6_address = null;

				try {
					Set<InetAddress> listedAdresses = subSlave.getChannel().call(new IPLister());

					for (InetAddress inetAddress: listedAdresses) {

						// FIXME: maybe should check availability from the ROOT slave, not from master?
						if (inetAddress != null && !inetAddress.isLinkLocalAddress() && isReachable(inetAddress) ) {
							if (inetAddress instanceof Inet6Address) {
								v6_address = inetAddress.getHostAddress();
							} else {
								v4_address = inetAddress.getHostAddress();
							}
						}
					}

					listener.getLogger().println("[compound-slave] Listed addresses of " + subSlave.getDisplayName() + " are: v4=" + v4_address + ", v6=" + v6_address);

					if (v4_address != null) {
						values.put(MessageFormat.format("{0}_{1}_{2}", role, i, "ipv4").toLowerCase(), v4_address);
					}
					if (v6_address != null) {
						values.put(MessageFormat.format("{0}_{1}_{2}", role, i, "ipv6").toLowerCase(), v6_address);
					}
					if (v4_address != null || v6_address != null) {
						// Use v4 address as default address
						String address = v4_address == null ? v6_address : v4_address;
						values.put(MessageFormat.format("{0}_{1}_{2}", role, i, "ip").toLowerCase(), address);
					}

				} catch (Exception e) {
					String message = "[compound-slave] Failed to get IP adress of " + subSlave.getDisplayName();
					listener.getLogger().println(message);
					throw new CompoundingException(message, e);
				}
			}
		}
		return values;
	}

	/**
	 * Callable that returns all non-loopback active IP addresses
	 *
	 * @author pupssman
	 */
	public static class IPLister implements Callable<Set<InetAddress>, IOException> {

		private static final long serialVersionUID = 1L;

		@Override
		public Set<InetAddress> call() throws IOException {
			HashSet<InetAddress> addresses = new HashSet<InetAddress>();

			Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();

			while (interfaces.hasMoreElements()) {
				NetworkInterface networkInterface = interfaces.nextElement();

				if (! networkInterface.isUp()) {
					continue; // take next interface
				}

				Enumeration<InetAddress> inetAddresses = networkInterface.getInetAddresses();

				while (inetAddresses.hasMoreElements()) {
					InetAddress address = inetAddresses.nextElement();

					if (!address.isLoopbackAddress()) {
						addresses.add(address);
					}
				}
			}

			return addresses;
		}
	}

	/**
	 * A smarter function that checks if address is reachable in the desired way. <br>
	 *
	 * Tries {@link InetAddress#isReachable(int)} first. <br>
	 *
	 * If that fails, tries to open socket to common ports (from {@link CompoundEnvironmentContributor#TEST_PORTS})
	 * to guess if that address is reachable.
	 *
	 * @param address to test
	 * @return if we've managed to reach the machine
	 */
	public static boolean isReachable(InetAddress address) {
		try {
			if(address.isReachable(PING_TIMEOUT)) {
				return true;
			}
		} catch (IOException e) {}

		Socket socket = new Socket();

		try {
			for (int port: TEST_PORTS) {
				try {
					socket.connect(new InetSocketAddress(address, port), PING_TIMEOUT);
					return true;
				} catch (IOException e) {}
			}

			return false;
		} finally {
			try {
				socket.close();
			} catch (IOException e) {}
		}
	}

}
