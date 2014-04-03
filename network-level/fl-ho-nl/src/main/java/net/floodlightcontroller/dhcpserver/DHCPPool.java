package net.floodlightcontroller.dhcpserver;


import java.util.Arrays;
import java.util.ArrayList;

import org.slf4j.Logger;

import net.floodlightcontroller.dhcpserver.DHCPBinding;
import net.floodlightcontroller.packet.Ethernet;
import net.floodlightcontroller.packet.IPv4;

/**
 * The class representing a DHCP Pool.
 * This class is essentially a list of DHCPBinding objects containing IP, MAC, and lease status information.
 *
 * @author Ryan Izard (rizard@g.clemson.edu)
 */
public class DHCPPool {
	protected Logger log;
	private volatile static ArrayList<DHCPBinding> DHCP_POOL = new ArrayList<DHCPBinding>();
	private volatile int POOL_SIZE;
	private volatile int POOL_AVAILABILITY;
	private volatile boolean POOL_FULL;
	private volatile byte[] STARTING_ADDRESS;
	private final byte[] UNASSIGNED_MAC = Ethernet.toMACAddress("00:00:00:00:00:00");

	// Need to write this to handle subnets later...
	// This assumes startingIPv4Address can handle size addresses
	/**
	 * Constructor for a DHCPPool of DHCPBinding's. Each DHCPBinding object is initialized with a
	 * null MAC address and the lease is set to inactive (i.e. false).
	 * @param {@code byte[]} startingIPv4Address: The lowest IP address to lease.
	 * @param {@code integer} size: (startingIPv4Address + size) is the highest IP address to lease.
	 * @return none
	 */
	public DHCPPool(byte[] startingIPv4Address, int size, Logger log) {
		this.log = log;
		int IPv4AsInt = IPv4.toIPv4Address(startingIPv4Address);
		this.setPoolSize(size);
		this.setPoolAvailability(size);
		this.setPoolFull(false);
		STARTING_ADDRESS = Arrays.copyOf(startingIPv4Address, startingIPv4Address.length);
		for (int i = 0; i < size; i++){ 
			DHCP_POOL.add(new DHCPBinding(IPv4.toIPv4AddressBytes(IPv4AsInt + i), UNASSIGNED_MAC));
		}

	}

	private void setPoolFull(boolean full) {
		POOL_FULL = full;
	}

	private boolean isPoolFull() {
		return POOL_FULL;
	}

	private void setPoolSize(int size) {
		POOL_SIZE = size;
	}

	private int getPoolSize() {
		return POOL_SIZE;
	}

	private int getPoolAvailability() {
		return POOL_AVAILABILITY;
	}

	private void setPoolAvailability(int size) {
		POOL_AVAILABILITY = size;
	}

	/**
	 * Gets the DHCPBinding object from the DHCPPool containing {@code byte[]} ip
	 * @param {@code byte[]} ip: The IPv4 address to match in a DHCPBinding
	 * @return {@code DHCPBinding}: The matching DHCPBinding object or null if ip is not found
	 */
	public DHCPBinding getDHCPbindingFromIPv4(byte[] ip) {
		if (ip == null) return null;
		for (DHCPBinding binding : DHCP_POOL) {
			if (Arrays.equals(binding.getIPv4AddressBytes(), ip)) {
				return binding;
			}
		}
		return null;
	}
	private DHCPBinding getLeasedDHCPbindingFromIPv4(byte[] ip) {
		if (ip == null) return null;
		for (DHCPBinding binding : DHCP_POOL) {
			if (Arrays.equals(binding.getIPv4AddressBytes(), ip) && binding.isActiveLease()) {
				return binding;
			}
		}
		return null;
	}
	/**
	 * Gets the DHCPBinding object from the DHCPPool containing {@code byte[]} mac
	 * @param {@code byte[]} mac: The MAC address to match in in a DHCPBinding
	 * @return {@code DHCPBinding}: The matching DHCPBinding object or null if mac is not found
	 */
	public DHCPBinding getDHCPbindingFromMAC(byte[] mac) {
		if (mac == null) return null;
		for (DHCPBinding binding : DHCP_POOL) {
			if (binding.isMACMemberOf(mac)) {
				return binding;
			}
		}
		return null;
	}
	private DHCPBinding getLeasedDHCPbindingFromMAC(byte[] mac) {
		if (mac == null) return null;
		for (DHCPBinding binding : DHCP_POOL) {
			if (binding.isMACMemberOf(mac) && binding.isActiveLease()) {
				return binding;
			}
		}
		return null;
	}
	
	public byte[] lookupBoundMAC(byte[] ip) {
		DHCPBinding binding = getLeasedDHCPbindingFromIPv4(ip);
		if (binding != null) {
			return binding.getCurrentMACAddressBytes();
		}
		return null;
	}
	
	public byte[] lookupBoundIP(byte[] mac) {
		DHCPBinding binding = getLeasedDHCPbindingFromMAC(mac);
		if (binding != null) {
			return binding.getIPv4AddressBytes();
		}
		return null;
	}
	
	/**
	 * Gets the lease status of a particular IPv4 address, {@code byte[]} ip
	 * @param {@code byte[]} ip: The IPv4 address of which to check the lease status 
	 * @return {@code boolean}: true if lease is active, false if lease is inactive/expired
	 */
	public boolean isIPv4Leased(byte[] ip) {
		DHCPBinding binding = this.getDHCPbindingFromIPv4(ip);
		if (binding != null) return binding.isActiveLease();
		else return false;
	}
	/**
	 * Assigns a MAC address to the IP address of the DHCPBinding object in the DHCPPool object.
	 * This method also sets the lease to active (i.e. true) when the assignment is made.
	 * @param {@code DHCPBinding} binding: The DHCPBinding object in which to set the MAC
	 * @param {@code byte[]} mac: The MAC address to set in the DHCPBinding object
	 * @param {@code long}: The time in seconds for which the lease will be valid
	 * @return none
	 */
	public void setDHCPbinding(DHCPBinding binding, byte[] mac, int time) {
		int index = DHCP_POOL.indexOf(binding);
		binding.addMACAddress(mac);
		// Bug Fixed! Needed to check if it's already leased to the same MAC before removing from pool!
		if (!binding.isActiveLease()) this.setPoolAvailability(this.getPoolAvailability() - 1);
		binding.setLeaseStatus(true);
		DHCP_POOL.set(index, binding);
		if (this.getPoolAvailability() == 0) setPoolFull(true);
		binding.setLeaseStartTimeSeconds();
		binding.setLeaseDurationSeconds(time);
	}
	/**
	 * Completely removes the DHCPBinding object with IP address {@code byte[]} ip from the DHCPPool
	 * @param {@code byte[]} ip: The IP address to remove from the pool. This address will not be available
	 * for lease after removal.
	 * @return none
	 */
	public void removeIPv4FromDHCPPool(byte [] ip) {
		if (ip == null || getDHCPbindingFromIPv4(ip) == null) return;
		if (IPv4.toIPv4Address(ip) == IPv4.toIPv4Address(STARTING_ADDRESS)) {
			DHCPBinding lowest = null;
			// Locate the lowest address (other than ip), which will be the new starting address
			for (DHCPBinding binding : DHCP_POOL) {
				if (lowest == null) {
					lowest = binding;
				} else if (IPv4.toIPv4Address(binding.getIPv4AddressBytes()) < IPv4.toIPv4Address(lowest.getIPv4AddressBytes())
						&& IPv4.toIPv4Address(binding.getIPv4AddressBytes()) != IPv4.toIPv4Address(ip))
				{
					lowest = binding;
				}
			}
			// lowest is new starting address
			STARTING_ADDRESS = Arrays.copyOf(lowest.getIPv4AddressBytes(), lowest.getIPv4AddressBytes().length);
		}
		DHCP_POOL.remove(this.getDHCPbindingFromIPv4(ip));
		this.setPoolSize(this.getPoolSize() - 1);
		this.setPoolAvailability(this.getPoolAvailability() - 1);
		if (this.getPoolAvailability() == 0) this.setPoolFull(true);
	}
	/**
	 * Adds an IP address to the DHCPPool if the address is not already present. If present, nothing is added to the DHCPPool.
	 * @param {@code byte[]} ip: The IP address to attempt to add to the DHCPPool
	 * @return {@code DHCPBinding}: Reference to the DHCPBinding object if successful, null if unsuccessful
	 */
	public DHCPBinding addIPv4ToDHCPPool(byte[] ip) {
		DHCPBinding binding = null;
		if (this.getDHCPbindingFromIPv4(ip) == null) {
			if (IPv4.toIPv4Address(ip) < IPv4.toIPv4Address(STARTING_ADDRESS)) {
				STARTING_ADDRESS = Arrays.copyOf(ip, ip.length);
			}
			binding = new DHCPBinding(ip, UNASSIGNED_MAC);
			DHCP_POOL.add(binding);
			this.setPoolSize(this.getPoolSize() + 1);
			this.setPoolFull(false);
		}
		return binding;
	}
	/**
	 * Determines if there are available leases in this DHCPPool.
	 * @return {@code boolean}: true if there are addresses available, false if the DHCPPool is full
	 */
	public boolean hasAvailableAddresses() {
		if (isPoolFull() || getPoolAvailability() == 0) return false;
		else return true;
	}
	/**
	 * Returns an available address (DHCPBinding) for lease.
	 * If this MAC is configured for a static/fixed IP, that DHCPBinding will be returned.
	 * If this MAC has had a lease before and that same lease is available, that DHCPBinding will be returned.
	 * If not, then an attempt to return an address that has not been active before will be made.
	 * If there are no addresses that have not been used, then the lowest currently inactive address will be returned.
	 * If all addresses are being used, then null will be returned.
	 * @param {@code byte[]): MAC address of the device requesting the lease
	 * @return {@code DHCPBinding}: Reference to the DHCPBinding object if successful, null if unsuccessful
	 */
	public DHCPBinding getAnyAvailableLease(byte[] mac) {
		if (isPoolFull()) return null;
		DHCPBinding usedBinding = null;
		usedBinding = this.getDHCPbindingFromMAC(mac);
		if (usedBinding != null) return usedBinding;

		for (DHCPBinding binding : DHCP_POOL) {
			if (!binding.isActiveLease() 
					&& binding.isMACMemberOf(UNASSIGNED_MAC) && binding.getNumberOfMACAddresses() != 1)
			{
				return binding;
			} else if (!binding.isActiveLease() && usedBinding == null && !binding.isStaticIPLease()) {
				usedBinding = binding;
			}
		}
		return usedBinding;
	}
	
	public DHCPBinding getStaticLease(byte[] mac) {
		if (isPoolFull()) return null;
		return this.getDHCPbindingFromMAC(mac);
	}

	/**
	 * Returns a specific available IP address binding for lease. The MAC and IP will be queried
	 * against the DHCP pool. (1) If the MAC is found in an available, fixed binding, and that binding
	 * is not for the provided IP, the fixed binding associated with the MAC will be returned. (2) If the
	 * IP is found in an available, fixed binding, and that binding also contains the MAC address provided, 
	 * then the binding will be returned -- this is true only if the IP and MAC result in the same available, 
	 * fixed binding. (3) If the IP is found in the pool and it is available and not fixed, then its
	 * binding will be returned. (4) If the IP provided does not match any available entries or is invalid, 
	 * null will be returned. If this is the case, run getAnyAvailableLease(mac) to resolve.
	 * @param {@code byte[]}: The IP address on which to try and obtain a lease
	 * @param {@code byte[]}: The MAC address on which to try and obtain a lease.
	 * @return {@code DHCPBinding}: Reference to the DHCPBinding object if successful, null if unsuccessful.
	 */
	public DHCPBinding getSpecificAvailableLease(byte[] ip, byte[] mac) {
		if (ip == null || mac == null || isPoolFull()) return null;

		DHCPBinding binding = this.getDHCPbindingFromIPv4(ip);
		DHCPBinding binding2 = this.getDHCPbindingFromMAC(mac);

		// For all of the following, the binding is also determined to be inactive:
		
		// If configured, we must return a fixed binding for a MAC address even if it's requesting another IP
		if (binding2 != null && !binding2.isActiveLease() && binding2.isStaticIPLease() && binding != binding2) {
			if (log != null) log.info("Fixed DHCP entry for MAC trumps requested IP. Returning binding for MAC");
			return binding2;
		// If configured, we must return a fixed binding for an IP if the binding is fixed to the provided MAC (ideal static request case)
		} else if (binding != null && !binding.isActiveLease() && binding.isStaticIPLease() && binding.isMACMemberOf(mac)) {
			if (log != null) log.info("Found matching fixed DHCP entry for IP with MAC. Returning binding for IP with MAC");
			return binding;
		// The IP and MAC are not a part of a fixed binding, so return the binding of the requested IP
		} else if (binding != null && !binding.isActiveLease() && !binding.isStaticIPLease()) {
			if (log != null) log.info("No fixed DHCP entry for IP or MAC found. Returning dynamic binding for IP.");
			return binding;
		// Otherwise, the binding is fixed for both MAC and IP and this MAC does not match either, so we can't return it as available
		} else {
			if (log != null) log.debug("Invalid IP address request or IP is actively leased...check for any available lease to resolve");
			return null;
		}
	}
	/**
	 * Tries to renew an IP lease.
	 * @param {@code byte[]}: The IP address on which to try and renew a lease
	 * @param {@code long}: The time in seconds for which the lease will be valid
	 * @return {@code DHCPBinding}: True on success, false if unknown IP address
	 */
	public boolean renewLease(byte[] ip, int time) {
		DHCPBinding binding = this.getDHCPbindingFromIPv4(ip);
		if (binding != null) {
			binding.setLeaseStartTimeSeconds();
			binding.setLeaseDurationSeconds(time);
			binding.setLeaseStatus(true);
			return true;
		}
		return false;
	}
	/**
	 * Cancel an IP lease.
	 * @param {@code byte[]}: The IP address on which to try and cancel a lease
	 * @return {@code boolean}: True on success, false if unknown IP address
	 */
	public boolean cancelLeaseOfIPv4(byte[] ip) {
		DHCPBinding binding = this.getDHCPbindingFromIPv4(ip);
		if (binding != null) {
			binding.clearLeaseTimes();
			binding.setLeaseStatus(false);
			if (!binding.isStaticIPLease()) {
				binding.clearMACAddresses();
			}
			this.setPoolAvailability(this.getPoolAvailability() + 1);
			this.setPoolFull(false);
			return true;
		}
		return false;
	}
	/**
	 * Cancel an IP lease.
	 * @param {@code byte[]}: The MAC address on which to try and cancel a lease
	 * @return {@code boolean}: True on success, false if unknown IP address
	 */
	public boolean cancelLeaseOfMAC(byte[] mac) {
		DHCPBinding binding = getDHCPbindingFromMAC(mac);
		if (binding != null) {
			binding.clearLeaseTimes();
			binding.setLeaseStatus(false);
			if (!binding.isStaticIPLease()) {
				binding.clearMACAddresses();
			}
			this.setPoolAvailability(this.getPoolAvailability() + 1);
			this.setPoolFull(false);
			return true;
		}
		return false;
	}
	/**
	 * Make the addresses of expired leases available and reset the lease times.
	 * @return {@code ArrayList<DHCPBinding>}: A list of the bindings that are now available
	 */
	public ArrayList<DHCPBinding> cleanExpiredLeases() {
		ArrayList<DHCPBinding> newAvailableLeases = new ArrayList<DHCPBinding>();
		for (DHCPBinding binding : DHCP_POOL) {
			// isLeaseExpired() automatically excludes configured static leases
			if (binding.isLeaseExpired() && binding.isActiveLease()) {
				this.cancelLeaseOfIPv4(binding.getIPv4AddressBytes());
				this.setPoolAvailability(this.getPoolAvailability() + 1);
				this.setPoolFull(false);
				newAvailableLeases.add(binding);
			}
		}
		return newAvailableLeases;
	}
	/**
	 * Used to set a particular IP binding in the pool as a fixed/static IP lease.
	 * This method does not set the lease as active, but instead reserves that IP
	 * for only the MAC provided. To set the lease as active, the methods getAnyAvailableLease()
	 * or getSpecificAvailableLease() will return the correct binding given the same
	 * MAC provided to this method is used to bind the lease later on.
	 * @param {@code byte[]}: The IP address to set as static/fixed.
	 * @param {@code byte[]}: The MAC address to match to the IP address ip when
	 * an address is requested from the MAC mac
	 * @return {@code boolean}: True upon success; false upon failure (e.g. no IP found)
	 */
	public boolean configureFixedIPLease(byte[] ip, ArrayList<byte[]> macs) {
		DHCPBinding binding = this.getDHCPbindingFromIPv4(ip);
		if (binding != null) {
			binding.setMACAddresses(macs);
			binding.setStaticIPLease(true);
			binding.setLeaseStatus(false);
			return true;
		} else {
			return false;
		}
	}

}