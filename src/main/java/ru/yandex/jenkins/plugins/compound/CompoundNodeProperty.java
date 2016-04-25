package ru.yandex.jenkins.plugins.compound;

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

import hudson.EnvVars;
import hudson.Extension;
import hudson.model.Slave;
import hudson.model.TaskListener;
import hudson.remoting.Callable;
import hudson.slaves.NodeProperty;
import hudson.slaves.NodePropertyDescriptor;

/**
 * a `NodeProperty` that would contribute this node's environment
 * 
 * @author pupssman
 */
public class CompoundNodeProperty extends NodeProperty<CompoundSlave> {

	private static final int[] TEST_PORTS = new int[] {22, 23, 80, 443, 8080};
	private static final int PING_TIMEOUT = 500;

	private Map<String, String> values = null;

	// Stored here instead of parent's .node because that one is transient
	private final CompoundSlave compoundSlave;

	public CompoundNodeProperty(CompoundSlave compoundSlave) {
		super();

		this.compoundSlave = compoundSlave;
	}

	@Override
	public void buildEnvVars(EnvVars env, TaskListener listener) throws IOException, InterruptedException {
		if (values == null) {
			try {
				listener.getLogger().println("[compound-slave] No environment known - computing...");

				values = computeValues(compoundSlave, listener);
			} catch (CompoundingException e) {
				throw new IOException(e);
			}
		}

		env.putAll(values);
	}
	
	@Extension
	public static class DescriptorImpl extends NodePropertyDescriptor {
		@Override
		public String getDisplayName() {
			return "Compoundy Stuff";
		}
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
		Map<String, String> values = new HashMap<String, String>();

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
