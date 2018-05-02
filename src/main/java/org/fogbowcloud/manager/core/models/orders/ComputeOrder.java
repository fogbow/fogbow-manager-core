package org.fogbowcloud.manager.core.models.orders;

import org.fogbowcloud.manager.core.instanceprovider.InstanceProvider;
import org.fogbowcloud.manager.core.models.token.Token;

public class ComputeOrder extends Order {

	private int vCPU;

	/** Memory attribute, must be set in MB. */
	private int memory;

	/** Disk attribute, must be set in GB. */
	private int disk;

	private String imageName;

	private UserData userData;

	public ComputeOrder() {
	}

	/**
	 * Creating Order with predefined Id.
	 */
	public ComputeOrder(String id, Token localToken, Token federationToken, String requestingMember, String providingMember,
			int vCPU, int memory, int disk, String imageName, UserData userData) {
		super(id, localToken, federationToken, requestingMember, providingMember);
		this.vCPU = vCPU;
		this.memory = memory;
		this.disk = disk;
		this.imageName = imageName;
		this.userData = userData;
	}
	
	public ComputeOrder(Token localToken, Token federationToken, String requestingMember, String providingMember,
			int vCPU, int memory, int disk, String imageName, UserData userData) {
		super(localToken, federationToken, requestingMember, providingMember);
		this.vCPU = vCPU;
		this.memory = memory;
		this.disk = disk;
		this.imageName = imageName;
		this.userData = userData;
	}

	public int getvCPU() {
		return vCPU;
	}

	public void setvCPU(int vCPU) {
		this.vCPU = vCPU;
	}

	public int getMemory() {
		return memory;
	}

	public void setMemory(int memory) {
		this.memory = memory;
	}

	public int getDisk() {
		return disk;
	}

	public void setDisk(int disk) {
		this.disk = disk;
	}

	public String getImageName() {
		return imageName;
	}

	public void setImageName(String imageName) {
		this.imageName = imageName;
	}

	public UserData getUserData() {
		return userData;
	}

	public void setUserData(UserData userData) {
		this.userData = userData;
	}

	@Override
	public OrderType getType() {
		return OrderType.COMPUTE;
	}

	/**
	 * These method handle and request an open order, for this, processOpenOrder
	 * handle the Order to be ready to change your state and request the
	 * Instance from the InstanceProvider.
	 */
	@Override
	public synchronized void processOpenOrder(InstanceProvider instanceProvider) {
		super.processOpenOrder(instanceProvider);
	}
}
