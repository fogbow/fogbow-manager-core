package cloud.fogbow.ras.core.plugins.interoperability.opennebula.compute.v5_4;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import cloud.fogbow.common.models.CloudUser;
import cloud.fogbow.common.models.SystemUser;
import cloud.fogbow.ras.constants.ConfigurationPropertyDefaults;
import cloud.fogbow.ras.constants.ConfigurationPropertyKeys;
import cloud.fogbow.ras.core.PropertiesHolder;
import cloud.fogbow.ras.core.TestUtils;
import cloud.fogbow.ras.core.datastore.DatabaseManager;
import cloud.fogbow.ras.core.models.HardwareRequirements;
import cloud.fogbow.ras.core.models.UserData;
import cloud.fogbow.ras.api.http.response.ComputeInstance;
import cloud.fogbow.ras.core.models.orders.ComputeOrder;
import cloud.fogbow.common.util.CloudInitUserDataBuilder;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.OpenNebulaBaseTests;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.OpenNebulaStateMapper;
import cloud.fogbow.ras.core.plugins.interoperability.util.DefaultLaunchCommandGenerator;
import cloud.fogbow.ras.core.plugins.interoperability.util.LaunchCommandGenerator;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.BDDMockito;
import org.mockito.Mockito;
import org.mockito.internal.verification.VerificationModeFactory;
import org.opennebula.client.Client;
import org.opennebula.client.OneResponse;
import org.opennebula.client.image.Image;
import org.opennebula.client.image.ImagePool;
import org.opennebula.client.template.Template;
import org.opennebula.client.template.TemplatePool;
import org.opennebula.client.vm.VirtualMachine;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.InvalidParameterException;
import cloud.fogbow.common.exceptions.NoAvailableResourcesException;
import cloud.fogbow.common.exceptions.QuotaExceededException;
import cloud.fogbow.common.exceptions.UnexpectedException;
import cloud.fogbow.ras.core.plugins.interoperability.opennebula.OpenNebulaClientUtil;

import static cloud.fogbow.ras.core.plugins.interoperability.opennebula.compute.v5_4.OpenNebulaComputePlugin.*;

@RunWith(PowerMockRunner.class)
@PrepareForTest({OpenNebulaClientUtil.class, VirtualMachine.class, DatabaseManager.class})
public class OpenNebulaComputePluginTest extends OpenNebulaBaseTests {
	private static final String FAKE_NAME = "fake-name";
	private static final String USER_NAME = PropertiesHolder.getInstance().getProperty(
			ConfigurationPropertyKeys.SSH_COMMON_USER_KEY, ConfigurationPropertyDefaults.SSH_COMMON_USER);
	private static final String FAKE_BASE64_SCRIPT = "fake-base64-script";

	private static final String ANOTHER_FAKE_NAME = "another-fake-name";
	private static final String DECIMAL_STRING_VALUE = "0.1";
	private static final String EMPTY_STRING = "";
	private static final String FAKE_HOST_NAME = "fake-host-name";
	private static final String FAKE_ID = "fake-id";
	private static final String FAKE_IMAGE = "fake-image";
	private static final String FAKE_IMAGE_ID = "fake-image-id";
	private static final String FAKE_INSTANCE_ID = "fake-instance-id";
	private static final String FAKE_PRIVATE_NETWORK_ID = "fake-private-network-id";
	private static final String FAKE_PUBLIC_KEY = "fake-public-key";
	private static final String FAKE_TAG = "fake-tag";
	private static final String FAKE_USER_DATA = "fake-user-data";
	private static final String FIELD_RESPONSE_LIMIT = "limit";
	private static final String FIELD_RESPONSE_QUOTA = "quota";
	private static final String FLAVOR_KIND_NAME = "smallest-flavor";
	private static final String ONE_STRING_VALUE = "1";
	private static final String IMAGE_SIZE_PATH = OpenNebulaComputePlugin.IMAGE_SIZE_PATH;
	private static final String IMAGE_SIZE_STRING_VALUE = "8192";
	private static final String IP_ADDRESS_ONE = "172.16.100.201";
	private static final String IP_ADDRESS_TWO = "172.16.100.202";
	private static final String LCM_STATE_RUNNING = "running";
	private static final String LOCAL_TOKEN_VALUE = "user:password";
	private static final String MEMORY_STRING_VALUE = "1024";
	private static final String MESSAGE_RESPONSE_ANYTHING = "anything";
	private static final String RESPONSE_NOT_ENOUGH_FREE_MEMORY = "Not enough free memory";
	private static final String SEPARATOR = " ";
	private static final String TEMPLATE_CPU_PATH = OpenNebulaComputePlugin.TEMPLATE_CPU_PATH;
	private static final String TEMPLATE_CPU_VALUE = "2";
	private static final String TEMPLATE_MEMORY_PATH = OpenNebulaComputePlugin.TEMPLATE_MEMORY_PATH;
	private static final String TEMPLATE_MEMORY_VALUE = "1024";
	private static final String TEMPLATE_IMAGE_ID_PATH = OpenNebulaComputePlugin.TEMPLATE_IMAGE_ID_PATH;
	private static final String CLOUD_NAME = "opennebula";

	private static final UserData[] FAKE_USER_DATA_ARRAY = new UserData[] {
			new UserData(FAKE_USER_DATA, CloudInitUserDataBuilder.FileType.CLOUD_CONFIG, FAKE_TAG) };

	private static final ArrayList<UserData> FAKE_LIST_USER_DATA = new ArrayList<>(Arrays.asList(FAKE_USER_DATA_ARRAY));

	private static final int CPU_VALUE_1 = 1;
	private static final int CPU_VALUE_2 = 2;
	private static final int CPU_VALUE_4 = 4;
	private static final int MEMORY_VALUE_1024 = 1024;
	private static final int MEMORY_VALUE_2048 = 2048;
	private static final int DISK_VALUE_6GB = 6144;
	private static final int DISK_VALUE_8GB = 8192;
	private static final int ZERO_VALUE = 0;
	private static final int DEFAULT_NETWORK_ID = ZERO_VALUE;

	private OpenNebulaComputePlugin plugin;
	private TreeSet<HardwareRequirements> flavors;
	private ComputeOrder computeOrder;
	private HardwareRequirements hardwareRequirements;
	private List<String> networkIds;

	@Before
	public void setUp() throws FogbowException {
	    super.setUp();

		this.plugin = Mockito.spy(new OpenNebulaComputePlugin(this.openNebulaConfFilePath));
		this.flavors = Mockito.spy(new TreeSet<>());
		this.computeOrder = this.getComputeOrder();
		this.hardwareRequirements = this.createHardwareRequirements();
		this.networkIds = this.listNetworkIds();
	}

	// test case: When calling the updateHardwareRequirements method with a set of
	// outdated flavors, a collection of valid resources must be obtained in the
	// cloud and compared to existing flavors, adding new flavors to the set when
	// they do not exist.
	@Test
	public void testUpdateHardwareRequirementsSuccessfully() throws UnexpectedException {
		// set up
		ImagePool imagePool = Mockito.mock(ImagePool.class);
		PowerMockito.mockStatic(OpenNebulaClientUtil.class);
		PowerMockito.when(OpenNebulaClientUtil.getImagePool(Mockito.any(Client.class))).thenReturn(imagePool);

		Image image = Mockito.mock(Image.class);
		Iterator<Image> imageIterator = Mockito.mock(Iterator.class);
		Mockito.when(imageIterator.hasNext()).thenReturn(true, false);
		Mockito.when(imageIterator.next()).thenReturn(image);
		Mockito.when(imagePool.iterator()).thenReturn(imageIterator);
		Mockito.when(image.xpath(IMAGE_SIZE_PATH)).thenReturn(IMAGE_SIZE_STRING_VALUE);

		TemplatePool templatePool = Mockito.mock(TemplatePool.class);
		PowerMockito.when(OpenNebulaClientUtil.getTemplatePool(Mockito.any(Client.class))).thenReturn(templatePool);

		Template template = Mockito.mock(Template.class);
		Iterator<Template> templateIterator = Mockito.mock(Iterator.class);
		Mockito.when(templateIterator.hasNext()).thenReturn(true, false);
		Mockito.when(templateIterator.next()).thenReturn(template);
		Mockito.when(templatePool.iterator()).thenReturn(templateIterator);
		Mockito.when(template.getId()).thenReturn(ONE_STRING_VALUE);
		Mockito.when(template.getName()).thenReturn(FAKE_NAME);
		Mockito.when(template.xpath(TEMPLATE_CPU_PATH)).thenReturn(ONE_STRING_VALUE);
		Mockito.when(template.xpath(TEMPLATE_MEMORY_PATH)).thenReturn(MEMORY_STRING_VALUE);
		Mockito.when(template.xpath(TEMPLATE_IMAGE_ID_PATH)).thenReturn(ONE_STRING_VALUE);

		Mockito.doReturn(DISK_VALUE_8GB).when(this.plugin).getDiskSizeFromImageSizeMap(Mockito.anyMap(), Mockito.anyString());

		int cpu = CPU_VALUE_1;
		int memory = MEMORY_VALUE_1024;
		int disk = DISK_VALUE_8GB;
		String flavorId = ONE_STRING_VALUE;
		String name = FAKE_NAME;
		HardwareRequirements flavor = new HardwareRequirements(name, flavorId, cpu, memory, disk);
		TreeSet<HardwareRequirements> expected = new TreeSet<HardwareRequirements>();
		expected.add(flavor);

		Client client = Mockito.mock(Client.class);

		// exercise
		this.plugin.updateHardwareRequirements(client);

		// verify
		PowerMockito.verifyStatic(OpenNebulaClientUtil.class, VerificationModeFactory.times(1));
		OpenNebulaClientUtil.getImagePool(Mockito.any(Client.class));

		PowerMockito.verifyStatic(OpenNebulaClientUtil.class, VerificationModeFactory.times(1));
		OpenNebulaClientUtil.getTemplatePool(Mockito.any(Client.class));

		Mockito.verify(image, Mockito.times(1)).xpath(Mockito.anyString());
		Mockito.verify(template, Mockito.times(1)).getId();
		Mockito.verify(template, Mockito.times(1)).getName();
		Mockito.verify(template, Mockito.times(3)).xpath(Mockito.anyString());
		Mockito.verify(this.plugin, Mockito.times(1)).getDiskSizeFromImageSizeMap(Mockito.anyMap(), Mockito.anyString());

		Assert.assertEquals(expected, this.plugin.getFlavors());
	}

	@Test
	public void testRequestInstance() throws FogbowException {
	    // set up
	    LaunchCommandGenerator launchCommandGenerator = Mockito.mock(LaunchCommandGenerator.class);

	    Mockito.when(launchCommandGenerator.createLaunchCommand(Mockito.any(ComputeOrder.class))).thenReturn(FAKE_BASE64_SCRIPT);
		Mockito.doReturn(this.networkIds).when(this.plugin).getNetworkIds(Mockito.anyList());
		Mockito.doReturn(this.hardwareRequirements).when(this.plugin).findSmallestFlavor(
				Mockito.any(Client.class), Mockito.any(ComputeOrder.class));
		Mockito.doReturn(this.getCreateComputeRequest()).when(this.plugin).createComputeRequest(
				Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyString(),
                Mockito.anyString(), Mockito.anyString(), Mockito.anyString(), Mockito.anyList(),
				Mockito.anyString());
		Mockito.doReturn(FAKE_ID).when(this.plugin).doRequestInstance(
				Mockito.any(Client.class), Mockito.any(CreateComputeRequest.class));

		// exercise
		this.plugin.requestInstance(this.computeOrder, this.cloudUser);

		// verify
		PowerMockito.verifyStatic(OpenNebulaClientUtil.class);
		OpenNebulaClientUtil.createClient(Mockito.anyString(), Mockito.eq(this.cloudUser.getToken()));

		Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE)).getNetworkIds(Mockito.eq(this.computeOrder.getNetworkIds()));
		Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE)).findSmallestFlavor(
				Mockito.eq(this.client), Mockito.eq(this.computeOrder));
		Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE)).createComputeRequest(
				Mockito.eq(this.computeOrder.getName()), Mockito.eq(NETWORK_CONFIRMATION_CONTEXT),
				Mockito.eq(this.computeOrder.getPublicKey()), Mockito.eq(USER_NAME), Mockito.eq(FAKE_BASE64_SCRIPT),
				Mockito.eq(String.valueOf(this.hardwareRequirements.getCpu())), Mockito.eq(DEFAULT_GRAPHIC_ADDRESS),
				Mockito.eq(DEFAULT_GRAPHIC_TYPE), Mockito.eq(this.computeOrder.getImageId()),
				Mockito.eq(String.valueOf(this.hardwareRequirements.getDisk())),
				Mockito.eq(String.valueOf(this.hardwareRequirements.getMemory())), Mockito.eq(this.networkIds),
				Mockito.eq(DEFAULT_ARCHITECTURE));
	}
 	
	// test case: When calling the updateHardwareRequirements method with an updated
	// set of flavors, nothing will be added to the existing flavor set.
	@Test
	public void testUpdateHardwareRequirementsWithUpdatedFlavorSet() throws UnexpectedException {
		// set up
		int cpu = CPU_VALUE_1;
		int memory = MEMORY_VALUE_1024;
		int disk = DISK_VALUE_8GB;
		String flavorId = ONE_STRING_VALUE;
		String name = FAKE_NAME;
		HardwareRequirements flavor = new HardwareRequirements(name, flavorId, cpu, memory, disk);
		TreeSet<HardwareRequirements> flavors = new TreeSet<HardwareRequirements>();
		flavors.add(flavor);
		this.plugin.setFlavors(flavors);
		int expected = this.plugin.getFlavors().size();

		ImagePool imagePool = Mockito.mock(ImagePool.class);
		PowerMockito.mockStatic(OpenNebulaClientUtil.class);
		PowerMockito.when(OpenNebulaClientUtil.getImagePool(Mockito.any(Client.class))).thenReturn(imagePool);

		Image image = Mockito.mock(Image.class);
		Iterator<Image> imageIterator = Mockito.mock(Iterator.class);
		Mockito.when(imageIterator.hasNext()).thenReturn(true, false);
		Mockito.when(imageIterator.next()).thenReturn(image);
		Mockito.when(imagePool.iterator()).thenReturn(imageIterator);
		Mockito.when(image.xpath(IMAGE_SIZE_PATH)).thenReturn(IMAGE_SIZE_STRING_VALUE);

		TemplatePool templatePool = Mockito.mock(TemplatePool.class);
		PowerMockito.when(OpenNebulaClientUtil.getTemplatePool(Mockito.any(Client.class))).thenReturn(templatePool);

		Template template = Mockito.mock(Template.class);
		Iterator<Template> templateIterator = Mockito.mock(Iterator.class);
		Mockito.when(templateIterator.hasNext()).thenReturn(true, false);
		Mockito.when(templateIterator.next()).thenReturn(template);
		Mockito.when(templatePool.iterator()).thenReturn(templateIterator);
		Mockito.when(template.getId()).thenReturn(ONE_STRING_VALUE);
		Mockito.when(template.getName()).thenReturn(FAKE_NAME);
		Mockito.when(template.xpath(TEMPLATE_CPU_PATH)).thenReturn(ONE_STRING_VALUE);
		Mockito.when(template.xpath(TEMPLATE_MEMORY_PATH)).thenReturn(MEMORY_STRING_VALUE);
		Mockito.when(template.xpath(TEMPLATE_IMAGE_ID_PATH)).thenReturn(ONE_STRING_VALUE);

		Mockito.doReturn(DISK_VALUE_8GB).when(this.plugin).getDiskSizeFromImageSizeMap(Mockito.anyMap(), Mockito.anyString());

		Client client = Mockito.mock(Client.class);

		// exercise
		this.plugin.updateHardwareRequirements(client);

		// verify
		PowerMockito.verifyStatic(OpenNebulaClientUtil.class, VerificationModeFactory.times(1));
		OpenNebulaClientUtil.getImagePool(Mockito.any(Client.class));

		PowerMockito.verifyStatic(OpenNebulaClientUtil.class, VerificationModeFactory.times(1));
		OpenNebulaClientUtil.getTemplatePool(Mockito.any(Client.class));

		Mockito.verify(image, Mockito.times(1)).xpath(Mockito.anyString());
		Mockito.verify(template, Mockito.times(1)).getId();
		Mockito.verify(template, Mockito.times(1)).getName();
		Mockito.verify(template, Mockito.times(3)).xpath(Mockito.anyString());
		Mockito.verify(this.plugin, Mockito.times(1)).getDiskSizeFromImageSizeMap(Mockito.anyMap(), Mockito.anyString());

		Assert.assertEquals(expected, this.plugin.getFlavors().size());
	}
	
	// test case: When calling the updateHardwareRequirements method and getting
	// invalid resource values in the cloud, it must not add them to the existing
	// flavor set.
	@Test
	public void testUpdateHardwareRequirementsWithInvalidResourceValue() throws UnexpectedException {
		// set up
		ImagePool imagePool = Mockito.mock(ImagePool.class);
		PowerMockito.mockStatic(OpenNebulaClientUtil.class);
		PowerMockito.when(OpenNebulaClientUtil.getImagePool(Mockito.any(Client.class))).thenReturn(imagePool);

		Image image = Mockito.mock(Image.class);
		Iterator<Image> imageIterator = Mockito.mock(Iterator.class);
		Mockito.when(imageIterator.hasNext()).thenReturn(true, false);
		Mockito.when(imageIterator.next()).thenReturn(image);
		Mockito.when(imagePool.iterator()).thenReturn(imageIterator);
		Mockito.when(image.xpath(IMAGE_SIZE_PATH)).thenReturn(IMAGE_SIZE_STRING_VALUE);

		TemplatePool templatePool = Mockito.mock(TemplatePool.class);
		PowerMockito.when(OpenNebulaClientUtil.getTemplatePool(Mockito.any(Client.class))).thenReturn(templatePool);

		Template template = Mockito.mock(Template.class);
		Iterator<Template> templateIterator = Mockito.mock(Iterator.class);
		Mockito.when(templateIterator.hasNext()).thenReturn(true, false);
		Mockito.when(templateIterator.next()).thenReturn(template);
		Mockito.when(templatePool.iterator()).thenReturn(templateIterator);
		Mockito.when(template.xpath(TEMPLATE_CPU_PATH)).thenReturn(DECIMAL_STRING_VALUE);
		Mockito.when(template.xpath(TEMPLATE_MEMORY_PATH)).thenReturn(DECIMAL_STRING_VALUE);
		Mockito.when(template.xpath(TEMPLATE_IMAGE_ID_PATH)).thenReturn(DECIMAL_STRING_VALUE);

		Mockito.doReturn(0).when(this.plugin).getDiskSizeFromImageSizeMap(Mockito.anyMap(), Mockito.anyString());

		TreeSet<HardwareRequirements> expected = new TreeSet<HardwareRequirements>();

		Client client = Mockito.mock(Client.class);

		// exercise
		this.plugin.updateHardwareRequirements(client);

		// verify
		PowerMockito.verifyStatic(OpenNebulaClientUtil.class, VerificationModeFactory.times(1));
		OpenNebulaClientUtil.getImagePool(Mockito.any(Client.class));

		PowerMockito.verifyStatic(OpenNebulaClientUtil.class, VerificationModeFactory.times(1));
		OpenNebulaClientUtil.getTemplatePool(Mockito.any(Client.class));

		Mockito.verify(image, Mockito.times(1)).xpath(Mockito.anyString());
		Mockito.verify(template, Mockito.times(3)).xpath(Mockito.anyString());
		Mockito.verify(this.plugin, Mockito.times(1)).getDiskSizeFromImageSizeMap(Mockito.anyMap(), Mockito.anyString());

		Assert.assertEquals(expected, this.plugin.getFlavors());
	}
	
	// test case: When calling the updateHardwareRequirements method and the flavor
	// collection is already up to date with the feature set in the cloud, it must
	// not add any existing flavors.
	@Test
	public void testUpdateHardwareRequirementsWithoutAnythingUpdatedFlavor() throws UnexpectedException {
		// set up
		ImagePool imagePool = Mockito.mock(ImagePool.class);
		PowerMockito.mockStatic(OpenNebulaClientUtil.class);
		PowerMockito.when(OpenNebulaClientUtil.getImagePool(Mockito.any(Client.class))).thenReturn(imagePool);

		Image image = Mockito.mock(Image.class);
		Iterator<Image> imageIterator = Mockito.mock(Iterator.class);
		Mockito.when(imageIterator.hasNext()).thenReturn(true, false);
		Mockito.when(imageIterator.next()).thenReturn(image);
		Mockito.when(imagePool.iterator()).thenReturn(imageIterator);
		Mockito.when(image.xpath(IMAGE_SIZE_PATH)).thenReturn(IMAGE_SIZE_STRING_VALUE);

		TemplatePool templatePool = Mockito.mock(TemplatePool.class);
		PowerMockito.when(OpenNebulaClientUtil.getTemplatePool(Mockito.any(Client.class))).thenReturn(templatePool);

		Template template = Mockito.mock(Template.class);
		Iterator<Template> templateIterator = Mockito.mock(Iterator.class);
		Mockito.when(templateIterator.hasNext()).thenReturn(true, false);
		Mockito.when(templateIterator.next()).thenReturn(template);
		Mockito.when(templatePool.iterator()).thenReturn(templateIterator);

		Client client = Mockito.mock(Client.class);

		// exercise
		this.plugin.updateHardwareRequirements(client);

		// verify
		PowerMockito.verifyStatic(OpenNebulaClientUtil.class, VerificationModeFactory.times(1));
		OpenNebulaClientUtil.getImagePool(Mockito.any(Client.class));

		PowerMockito.verifyStatic(OpenNebulaClientUtil.class, VerificationModeFactory.times(1));
		OpenNebulaClientUtil.getTemplatePool(Mockito.any(Client.class));

		Mockito.verify(image, Mockito.times(1)).xpath(Mockito.anyString());
		Mockito.verify(template, Mockito.times(1)).getId();
		Mockito.verify(template, Mockito.times(1)).getName();
		Mockito.verify(template, Mockito.times(3)).xpath(Mockito.anyString());
		Mockito.verify(this.plugin, Mockito.times(1)).getDiskSizeFromImageSizeMap(Mockito.anyMap(), Mockito.anyString());
	}
    
	// test case: When calling the getComputeInstance method passing a valid virtual
	// machine, it must obtain the data to mount an instance of this resource.
	@Test
	public void testGetComputeInstanceSuccessfully() {
		// set up
		String id = FAKE_ID;
		String name = FAKE_NAME;
		int cpu = CPU_VALUE_1;
		int memory = MEMORY_VALUE_1024;
		int disk = DISK_VALUE_8GB;
		List<String> ipAddresses = new ArrayList<String>();
		ipAddresses.add(IP_ADDRESS_ONE);
		ipAddresses.add(IP_ADDRESS_TWO);

		VirtualMachine virtualMachine = Mockito.mock(VirtualMachine.class);
		Mockito.when(virtualMachine.getId()).thenReturn(id);
		Mockito.when(virtualMachine.getName()).thenReturn(name);
		Mockito.when(virtualMachine.lcmStateStr()).thenReturn(LCM_STATE_RUNNING);
		Mockito.when(virtualMachine.xpath(OpenNebulaComputePlugin.TEMPLATE_CPU_PATH)).thenReturn(ONE_STRING_VALUE);
		Mockito.when(virtualMachine.xpath(OpenNebulaComputePlugin.TEMPLATE_DISK_SIZE_PATH))
				.thenReturn(IMAGE_SIZE_STRING_VALUE);
		Mockito.when(virtualMachine.xpath(OpenNebulaComputePlugin.TEMPLATE_MEMORY_PATH))
				.thenReturn(MEMORY_STRING_VALUE);

		String xml = getVirtualMachineResponse();
		OneResponse response = Mockito.mock(OneResponse.class);
		Mockito.when(virtualMachine.info()).thenReturn(response);
		Mockito.when(response.isError()).thenReturn(false);
		Mockito.when(response.getMessage()).thenReturn(xml);

		ComputeInstance expected = new ComputeInstance(id, OpenNebulaStateMapper.COMPUTE_RUNNING_STATE, name, cpu, memory, disk, ipAddresses);

		// exercise
		ComputeInstance computeInstance = this.plugin.doGetComputeInstance(virtualMachine);

		// verify
		Mockito.verify(virtualMachine, Mockito.times(1)).info();
		Mockito.verify(virtualMachine, Mockito.times(1)).getId();
		Mockito.verify(virtualMachine, Mockito.times(1)).getName();
		Mockito.verify(virtualMachine, Mockito.times(1)).lcmStateStr();
		Mockito.verify(virtualMachine, Mockito.times(3)).xpath(Mockito.anyString());
		Mockito.verify(response, Mockito.times(1)).getMessage();

		Assert.assertEquals(expected, computeInstance);
	}

	// test case: When calling the getDisckSizeFromImages method with valid
	// parameters, it must return a valid disk size.
	@Test
	public void testGetDiskSizeFromImagesSuccessfully() {
		// set up
		String imageId = ONE_STRING_VALUE;
		String imageSize = IMAGE_SIZE_STRING_VALUE;
		Map<String, String> imageSizeMap = new HashMap<String, String>();
		imageSizeMap.put(imageId, imageSize);

		int expected = Integer.parseInt(imageSize);

		// exercise
		int value = this.plugin.getDiskSizeFromImageSizeMap(imageSizeMap, imageId);

		// verify
		Assert.assertEquals(expected, value);
	}
    
	// test case: When calling the getDisckSizeFromImages method with the null
	// imageSizeMap parameter, it must log the error that occurred and return the
	// value zero.
	@Test
	public void testGetDiskSizeFromImagesWithImageSizeMapNull() {
		// set up
		String imageId = FAKE_IMAGE_ID;
		Map<String, String> imageSizeMap = null;
		int expected = ZERO_VALUE;

		// exercise
		int value = this.plugin.getDiskSizeFromImageSizeMap(imageSizeMap, imageId);

		// verify
		Assert.assertEquals(expected, value);
	}
    
	// test case: When calling the getDisckSizeFromImages method with the null
	// imageId parameter, it must log the error that occurred and return the value
	// zero.
	@Test
	public void testGetDiskSizeFromImagesWithImageIdNull() {
		// set up
		String imageId = null;
		Map<String, String> imageSizeMap = new HashMap<String, String>();
		int expected = ZERO_VALUE;

		// exercise
		int value = this.plugin.getDiskSizeFromImageSizeMap(imageSizeMap, imageId);

		// verify
		Assert.assertEquals(expected, value);
	}
    
	// test case: When calling the getDisckSizeFromImages method with the two null
	// parameters, it must log the error that occurred and return the value zero.
	@Test
	public void testGetDiskSizeFromImagesWithTwoParametersNull() {
		// set up
		String imageId = null;
		Map<String, String> imageSizeMap = null;
		int expected = ZERO_VALUE;

		// exercise
		int value = this.plugin.getDiskSizeFromImageSizeMap(imageSizeMap, imageId);

		// verify
		Assert.assertEquals(expected, value);
	}
    
	// test case: When calling the convertToInteger method with a valid numeric
	// string, it will be converted to a valid integer value.
	@Test
	public void testConvertToIntegerSuccessfully() {
		// set up
		String number = TEMPLATE_CPU_VALUE;
		int expected = CPU_VALUE_2;

		// exercise
		int value = this.plugin.convertToInteger(number);

		// verify
		Assert.assertEquals(expected, value);
	}
    
	// test case: When calling the convertToInteger method with an invalid numeric
	// string, it must register the error and return the value zero.
	@Test
	public void testConvertToIntegerUnsuccessfully() {
		// set up
		String number = EMPTY_STRING;
		int expected = ZERO_VALUE;

		// exercise
		int value = this.plugin.convertToInteger(number);

		// verify
		Assert.assertEquals(expected, value);
	}
    
	// test case: When calling the containsFlavor method as an existing flavor in
	// the collection, it must return true.
	@Test
	public void testContainsFlavor() {
		// set up
		int cpu = CPU_VALUE_1;
		int memory = MEMORY_VALUE_1024;
		int disk = DISK_VALUE_6GB;
		String flavorId = FAKE_ID;
		String name = FAKE_NAME;
		HardwareRequirements flavor = new HardwareRequirements(name, flavorId, cpu, memory, disk);
		List<HardwareRequirements> flavors = new ArrayList<>();
		flavors.add(flavor);

		// exercise
		boolean condition = this.plugin.containsFlavor(flavor);

		// verify
		Assert.assertTrue(condition);
	}
    
	// test case: When calling the containsFlavor method as a flavor that does not
	// exist in the collection, it must return false.
	@Test
	public void testNotContainsFlavor() {
		// set up
		int cpu = CPU_VALUE_1;
		int memory = MEMORY_VALUE_1024;
		int disk = DISK_VALUE_6GB;
		String flavorId = FAKE_ID;
		String name = FAKE_NAME;
		String anotherName = ANOTHER_FAKE_NAME;
		HardwareRequirements flavor = new HardwareRequirements(name, flavorId, cpu, memory, disk);
		HardwareRequirements anotherFlavor = new HardwareRequirements(anotherName, flavorId, cpu, memory, disk);
		List<HardwareRequirements> flavors = new ArrayList<>();
		flavors.add(flavor);

		// exercise
		boolean condition = this.plugin.containsFlavor(anotherFlavor);

		// verify
		Assert.assertFalse(condition);
	}

    
	// test case: When calling the requestInstance method, with the valid client and
	// template with a list of networks ID, a virtual network will be allocated,
	// returned a instance ID.
    @Test
	public void testRequestInstanceSuccessfulWithTwoNetworkIds() throws FogbowException, UnexpectedException {
		// set up
		Client client = Mockito.mock(Client.class);
		PowerMockito.mockStatic(OpenNebulaClientUtil.class);
		BDDMockito.given(OpenNebulaClientUtil.createClient(Mockito.anyString(), Mockito.anyString()))
				.willReturn(client);

		LaunchCommandGenerator mockLaunchCommandGenerator = Mockito.spy(new DefaultLaunchCommandGenerator());
		Mockito.doReturn(FAKE_USER_DATA).when(mockLaunchCommandGenerator).createLaunchCommand(Mockito.any());
		this.plugin.setLaunchCommandGenerator(mockLaunchCommandGenerator);
		
		ImagePool imagePool = Mockito.mock(ImagePool.class);
		BDDMockito.given(OpenNebulaClientUtil.getImagePool(Mockito.any())).willReturn(imagePool);

		Image image = Mockito.mock(Image.class);
		Iterator<Image> imageIterator = Mockito.mock(Iterator.class);
		Mockito.when(imageIterator.hasNext()).thenReturn(true, false);
		Mockito.when(imageIterator.next()).thenReturn(image);
		Mockito.when(imagePool.iterator()).thenReturn(imageIterator);
		Mockito.when(image.xpath(IMAGE_SIZE_PATH)).thenReturn(IMAGE_SIZE_STRING_VALUE);

		HardwareRequirements flavor = createHardwareRequirements();
		this.flavors.add(flavor);
		this.plugin.setFlavors(this.flavors);
		Mockito.doReturn(true).when(this.plugin).containsFlavor(Mockito.eq(flavor));

		OneResponse response = Mockito.mock(OneResponse.class);
		PowerMockito.mockStatic(VirtualMachine.class);
		PowerMockito.when(VirtualMachine.allocate(Mockito.any(Client.class), Mockito.anyString())).thenReturn(response);
		Mockito.when(response.isError()).thenReturn(false);

		List<String> networkIds = listNetworkIds();
		ComputeOrder computeOrder = createComputeOrder(networkIds, CPU_VALUE_2, MEMORY_VALUE_1024, DISK_VALUE_8GB);
		CloudUser cloudUser = createCloudUser();
		
		int choice = 0;
		String valueOfCpu = String.valueOf(CPU_VALUE_4);
		String valueOfRam = String.valueOf(MEMORY_VALUE_2048);
		String valueOfDisk = String.valueOf(DISK_VALUE_8GB);
		String defaultNetworkId = String.valueOf(DEFAULT_NETWORK_ID);
		String privateNetworkId = FAKE_PRIVATE_NETWORK_ID;
		String virtualMachineTemplate = generateTemplate(choice, valueOfCpu, valueOfRam, defaultNetworkId,
				privateNetworkId, valueOfDisk);
		
		// exercise
		this.plugin.requestInstance(computeOrder, cloudUser);

		// verify
		PowerMockito.verifyStatic(OpenNebulaClientUtil.class, VerificationModeFactory.times(2));
		OpenNebulaClientUtil.createClient(Mockito.anyString(), Mockito.anyString());

		PowerMockito.verifyStatic(OpenNebulaClientUtil.class, VerificationModeFactory.times(1));
		OpenNebulaClientUtil.getImagePool(Mockito.eq(client));

		Mockito.verify(this.plugin, Mockito.times(1)).findSmallestFlavor(Mockito.any(Client.class), Mockito.any(ComputeOrder.class));

		PowerMockito.verifyStatic(OpenNebulaClientUtil.class, VerificationModeFactory.times(1));
		OpenNebulaClientUtil.allocateVirtualMachine(Mockito.any(Client.class), Mockito.eq(virtualMachineTemplate));
	}
	
	// test case: When calling the requestInstance method, with the valid client and
	// template without a list of networks ID, a virtual network will be allocated,
	// returned a instance ID.
	@Test
	public void testRequestInstanceSuccessfulWithoutNetworksId() throws FogbowException {
		// set up
		Client client = Mockito.mock(Client.class);
		PowerMockito.mockStatic(OpenNebulaClientUtil.class);
		BDDMockito.given(OpenNebulaClientUtil.createClient(Mockito.anyString(), Mockito.anyString()))
				.willReturn(client);

		LaunchCommandGenerator mockLaunchCommandGenerator = Mockito.spy(new DefaultLaunchCommandGenerator());
		Mockito.doReturn(FAKE_USER_DATA).when(mockLaunchCommandGenerator).createLaunchCommand(Mockito.any());
		this.plugin.setLaunchCommandGenerator(mockLaunchCommandGenerator);
		
		CloudUser cloudUser = createCloudUser();
		List<String> networkIds = null;
		ComputeOrder computeOrder = createComputeOrder(networkIds, CPU_VALUE_1, MEMORY_VALUE_2048, DISK_VALUE_8GB);
		HardwareRequirements flavor = createHardwareRequirements();
		Mockito.doReturn(flavor).when(this.plugin).findSmallestFlavor(client, computeOrder);

		OneResponse response = Mockito.mock(OneResponse.class);
		PowerMockito.mockStatic(VirtualMachine.class);
		PowerMockito.when(VirtualMachine.allocate(Mockito.any(Client.class), Mockito.anyString())).thenReturn(response);
		Mockito.when(response.isError()).thenReturn(false);
		
		int choice = 1;
		String networkId = null;
		String valueOfCpu = String.valueOf(CPU_VALUE_4);
		String valueOfRam = String.valueOf(MEMORY_VALUE_2048);
		String valueOfDisk = String.valueOf(DISK_VALUE_8GB);
		String template = generateTemplate(choice, valueOfCpu, valueOfRam, networkId, valueOfDisk);

		// exercise
		this.plugin.requestInstance(computeOrder, cloudUser);

		// verify
		PowerMockito.verifyStatic(OpenNebulaClientUtil.class, VerificationModeFactory.times(1));
		OpenNebulaClientUtil.createClient(Mockito.anyString(), Mockito.anyString());

		Mockito.verify(this.plugin, Mockito.times(1)).findSmallestFlavor(Mockito.any(Client.class),
				Mockito.any(ComputeOrder.class));

		PowerMockito.verifyStatic(OpenNebulaClientUtil.class, VerificationModeFactory.times(1));
		OpenNebulaClientUtil.allocateVirtualMachine(Mockito.any(Client.class), Mockito.eq(template));
	}
	
	// test case: When calling the requestInstance method, with a disk size smaller
	// than the image, a configuration for volatile disk will be defined in the
	// model for allocation, returning an instance ID.
	@Test
	public void testRequestInstanceSuccessfulWithVolatileDisk() throws FogbowException {
		// set up
		Client client = Mockito.mock(Client.class);
		PowerMockito.mockStatic(OpenNebulaClientUtil.class);
		BDDMockito.given(OpenNebulaClientUtil.createClient(Mockito.anyString(), Mockito.anyString()))
				.willReturn(client);

		LaunchCommandGenerator mockLaunchCommandGenerator = Mockito.spy(new DefaultLaunchCommandGenerator());
		Mockito.doReturn(FAKE_USER_DATA).when(mockLaunchCommandGenerator).createLaunchCommand(Mockito.any());
		this.plugin.setLaunchCommandGenerator(mockLaunchCommandGenerator);

		ImagePool imagePool = Mockito.mock(ImagePool.class);
		BDDMockito.given(OpenNebulaClientUtil.getImagePool(Mockito.any())).willReturn(imagePool);

		Image image = Mockito.mock(Image.class);
		Iterator<Image> imageIterator = Mockito.mock(Iterator.class);
		Mockito.when(imageIterator.hasNext()).thenReturn(true, false);
		Mockito.when(imageIterator.next()).thenReturn(image);
		Mockito.when(imagePool.iterator()).thenReturn(imageIterator);
		Mockito.when(image.xpath(IMAGE_SIZE_PATH)).thenReturn(IMAGE_SIZE_STRING_VALUE);

		HardwareRequirements flavor = createHardwareRequirements();
		this.flavors.add(flavor);
		this.plugin.setFlavors(this.flavors);
		Mockito.doReturn(true).when(this.plugin).containsFlavor(Mockito.eq(flavor));

		OneResponse response = Mockito.mock(OneResponse.class);
		PowerMockito.mockStatic(VirtualMachine.class);
		PowerMockito.when(VirtualMachine.allocate(Mockito.any(Client.class), Mockito.anyString())).thenReturn(response);
		Mockito.when(response.isError()).thenReturn(false);

		List<String> networkIds = null;
		ComputeOrder computeOrder = createComputeOrder(networkIds, CPU_VALUE_2, MEMORY_VALUE_1024, DISK_VALUE_6GB);
		CloudUser cloudUser = createCloudUser();

		int choice = 2;
		String valueOfCpu = String.valueOf(CPU_VALUE_4);
		String valueOfRam = String.valueOf(MEMORY_VALUE_2048);
		String valueOfDisk = String.valueOf(DISK_VALUE_6GB);
		String defaultNetworkId = String.valueOf(DEFAULT_NETWORK_ID);
		String virtualMachineTemplate = generateTemplate(choice, valueOfCpu, valueOfRam, defaultNetworkId, valueOfDisk);

		// exercise
		this.plugin.requestInstance(computeOrder, cloudUser);

		// verify
		PowerMockito.verifyStatic(OpenNebulaClientUtil.class, VerificationModeFactory.times(2));
		OpenNebulaClientUtil.createClient(Mockito.anyString(), Mockito.anyString());

		PowerMockito.verifyStatic(OpenNebulaClientUtil.class, VerificationModeFactory.times(1));
		OpenNebulaClientUtil.getImagePool(Mockito.eq(client));

		Mockito.verify(this.plugin, Mockito.times(1)).findSmallestFlavor(Mockito.any(Client.class),
				Mockito.any(ComputeOrder.class));

		PowerMockito.verifyStatic(OpenNebulaClientUtil.class, VerificationModeFactory.times(1));
		OpenNebulaClientUtil.allocateVirtualMachine(Mockito.any(Client.class), Mockito.eq(virtualMachineTemplate));
	}
	
	// test case: When calling getBestFlavor during requestInstance method, and it
	// returns a null instance, a NoAvailableResourcesException will be thrown.
	@Test(expected = NoAvailableResourcesException.class) // verify
	public void testRequestInstanceThrowNoAvailableResourcesExceptionWhenCallGetBestFlavor() throws FogbowException {
		// set up
		Client client = Mockito.mock(Client.class);
		PowerMockito.mockStatic(OpenNebulaClientUtil.class);
		BDDMockito.given(OpenNebulaClientUtil.createClient(Mockito.anyString(), Mockito.anyString()))
				.willReturn(client);

		ImagePool imagePool = Mockito.mock(ImagePool.class);
		BDDMockito.given(OpenNebulaClientUtil.getImagePool(Mockito.any())).willReturn(imagePool);

		Image image = Mockito.mock(Image.class);
		Iterator<Image> imageIterator = Mockito.mock(Iterator.class);
		Mockito.when(imageIterator.hasNext()).thenReturn(true, false);
		Mockito.when(imageIterator.next()).thenReturn(image);
		Mockito.when(imagePool.iterator()).thenReturn(imageIterator);
		Mockito.when(image.xpath(IMAGE_SIZE_PATH)).thenReturn(IMAGE_SIZE_STRING_VALUE);

		CloudUser cloudUser = createCloudUser();
		List<String> networkIds = null;
		ComputeOrder computeOrder = createComputeOrder(networkIds, CPU_VALUE_1, MEMORY_VALUE_2048, DISK_VALUE_8GB);

		// exercise
		this.plugin.requestInstance(computeOrder, cloudUser);
	}
	
	// test case: When calling the requestInstance method, and an error not
	// specified occurs while attempting to allocate a virtual machine, an
	// InvalidParameterException will be thrown.
	@Test(expected = InvalidParameterException.class) // verify
	public void testRequestInstanceThrowInvalidParameterException() throws FogbowException {
		// set up
		CloudUser cloudUser = createCloudUser();
		List<String> networkIds = null;
		ComputeOrder computeOrder = createComputeOrder(networkIds, CPU_VALUE_4, MEMORY_VALUE_2048, DISK_VALUE_8GB);
		HardwareRequirements flavor = createHardwareRequirements();
		Client client = Mockito.mock(Client.class);
		Mockito.doReturn(flavor).when(this.plugin).findSmallestFlavor(client, computeOrder);

		LaunchCommandGenerator mockLaunchCommandGenerator = Mockito.spy(new DefaultLaunchCommandGenerator());
		Mockito.doReturn(FAKE_USER_DATA).when(mockLaunchCommandGenerator).createLaunchCommand(Mockito.any());
		this.plugin.setLaunchCommandGenerator(mockLaunchCommandGenerator);

		OneResponse response = Mockito.mock(OneResponse.class);
		PowerMockito.mockStatic(VirtualMachine.class);
		PowerMockito.when(VirtualMachine.allocate(Mockito.any(Client.class), Mockito.anyString())).thenReturn(response);
		Mockito.when(response.isError()).thenReturn(true);
		Mockito.when(response.getErrorMessage()).thenReturn(MESSAGE_RESPONSE_ANYTHING);

		int choice = 1;
		String networkId = null;
		String valueOfCpu = String.valueOf(CPU_VALUE_4);
		String valueOfRam = String.valueOf(MEMORY_VALUE_2048);
		String valueOfDisk = String.valueOf(DISK_VALUE_8GB);
		String template = generateTemplate(choice, valueOfCpu, valueOfRam, networkId, valueOfDisk);

		// exercise
		this.plugin.requestInstance(computeOrder, cloudUser);
	}
	
	// test case: When you attempt to allocate a virtual machine with the
	// requestInstance method call, and an insufficient free memory error message
	// occurs, a NoAvailableResourcesException will be thrown.
	@Test(expected = NoAvailableResourcesException.class) // verify
	public void testRequestInstanceWithMemoryInsufficientThrowNoAvailableResourcesException() throws FogbowException {
		// set up
		CloudUser cloudUser = createCloudUser();

		List<String> networkIds = null;
		ComputeOrder computeOrder = createComputeOrder(networkIds, CPU_VALUE_4, MEMORY_VALUE_2048, DISK_VALUE_8GB);
		HardwareRequirements flavor = createHardwareRequirements();
		Client client = Mockito.mock(Client.class);
		Mockito.doReturn(flavor).when(this.plugin).findSmallestFlavor(client, computeOrder);

		LaunchCommandGenerator mockLaunchCommandGenerator = Mockito.spy(new DefaultLaunchCommandGenerator());
		Mockito.doReturn(FAKE_USER_DATA).when(mockLaunchCommandGenerator).createLaunchCommand(Mockito.any());
		this.plugin.setLaunchCommandGenerator(mockLaunchCommandGenerator);

		int choice = 1;
		String networkId = null;
		String valueOfCpu = String.valueOf(CPU_VALUE_4);
		String valueOfRam = String.valueOf(MEMORY_VALUE_2048);
		String valueOfDisk = String.valueOf(DISK_VALUE_8GB);
		String template = generateTemplate(choice, valueOfCpu, valueOfRam, networkId, valueOfDisk);

		OneResponse response = Mockito.mock(OneResponse.class);
		PowerMockito.mockStatic(VirtualMachine.class);
		PowerMockito.when(VirtualMachine.allocate(Mockito.any(Client.class), Mockito.anyString())).thenReturn(response);
		Mockito.when(response.isError()).thenReturn(true);
		Mockito.when(response.getErrorMessage()).thenReturn(RESPONSE_NOT_ENOUGH_FREE_MEMORY);

		// exercise
		this.plugin.requestInstance(computeOrder, cloudUser);
	}
	
	// test case: When attempting to allocate a virtual machine with the
	// requestInstance method call, and an error message occurs with the words limit
	// and quota, a QuotaExceededException will be thrown.
	@Test(expected = QuotaExceededException.class) // verify
	public void testRequestInstanceThrowQuotaExceededException() throws FogbowException {
		// set up
		CloudUser cloudUser = createCloudUser();
		List<String> networkIds = null;
		ComputeOrder computeOrder = createComputeOrder(networkIds, CPU_VALUE_4, MEMORY_VALUE_2048, DISK_VALUE_8GB);
		HardwareRequirements flavor = createHardwareRequirements();
		Client client = Mockito.mock(Client.class);
		Mockito.doReturn(flavor).when(this.plugin).findSmallestFlavor(client, computeOrder);

		LaunchCommandGenerator mockLaunchCommandGenerator = Mockito.spy(new DefaultLaunchCommandGenerator());
		Mockito.doReturn(FAKE_USER_DATA).when(mockLaunchCommandGenerator).createLaunchCommand(Mockito.any());
		this.plugin.setLaunchCommandGenerator(mockLaunchCommandGenerator);

		int choice = 1;
		String networkId = null;
		String valueOfCpu = String.valueOf(CPU_VALUE_4);
		String valueOfRam = String.valueOf(MEMORY_VALUE_2048);
		String valueOfDisk = String.valueOf(DISK_VALUE_8GB);
		String template = generateTemplate(choice, valueOfCpu, valueOfRam, networkId, valueOfDisk);

		String message = FIELD_RESPONSE_LIMIT + SEPARATOR + FIELD_RESPONSE_QUOTA;
		
		OneResponse response = Mockito.mock(OneResponse.class);
		PowerMockito.mockStatic(VirtualMachine.class);
		PowerMockito.when(VirtualMachine.allocate(Mockito.any(Client.class), Mockito.anyString())).thenReturn(response);
		Mockito.when(response.isError()).thenReturn(true);
		Mockito.when(response.getErrorMessage()).thenReturn(message);

		// exercise
		this.plugin.requestInstance(computeOrder, cloudUser);
	}
	
	// test case: When calling the getInstance method of a resource without the volatile disk size passing 
	// a valid client of a token value and an instance ID, it must return an instance of a virtual machine.
	@Test
	public void testGetInstanceSuccessfulWithoutVolatileDiskResource() throws FogbowException {
		// set up
		Client client = Mockito.mock(Client.class);
		PowerMockito.mockStatic(OpenNebulaClientUtil.class);
		BDDMockito.given(OpenNebulaClientUtil.createClient(Mockito.anyString(), Mockito.anyString()))
				.willReturn(client);

		HardwareRequirements flavor = createHardwareRequirements();
		this.flavors.add(flavor);
		this.plugin.setFlavors(this.flavors);

		VirtualMachine virtualMachine = Mockito.mock(VirtualMachine.class);
		BDDMockito.given(OpenNebulaClientUtil.getVirtualMachine(Mockito.eq(client), Mockito.anyString()))
				.willReturn(virtualMachine);

		ComputeInstance computeInstance = CreateComputeInstance();
		Mockito.doReturn(computeInstance).when(this.plugin).doGetComputeInstance(virtualMachine);

		CloudUser cloudUser = createCloudUser();
		String instanceId = FAKE_INSTANCE_ID;

		ComputeOrder computeOrder = new ComputeOrder();
		computeOrder.setInstanceId(instanceId);

		// exercise
		this.plugin.getInstance(computeOrder, cloudUser);

		// verify
		PowerMockito.verifyStatic(OpenNebulaClientUtil.class, VerificationModeFactory.times(1));
		OpenNebulaClientUtil.createClient(Mockito.anyString(), Mockito.anyString());

		PowerMockito.verifyStatic(OpenNebulaClientUtil.class, VerificationModeFactory.times(1));
		OpenNebulaClientUtil.getVirtualMachine(Mockito.eq(client), Mockito.anyString());

		Mockito.verify(this.plugin, Mockito.times(1)).doGetComputeInstance(virtualMachine);
	}
	
	// Test case: When calling the deleteInstance method, with the instance ID and
	// token valid, the instance of virtual machine will be removed.
	@Test
	public void testDeleteInstanceSuccessful() throws FogbowException {
		// set up
		Client client = Mockito.mock(Client.class);
		PowerMockito.mockStatic(OpenNebulaClientUtil.class);
		BDDMockito.given(OpenNebulaClientUtil.createClient(Mockito.anyString(), Mockito.anyString()))
				.willReturn(client);

		VirtualMachine virtualMachine = Mockito.mock(VirtualMachine.class);
		BDDMockito.given(OpenNebulaClientUtil.getVirtualMachine(Mockito.eq(client), Mockito.anyString()))
				.willReturn(virtualMachine);

		OneResponse response = Mockito.mock(OneResponse.class);
		Mockito.doReturn(response).when(virtualMachine).terminate(OpenNebulaComputePlugin.SHUTS_DOWN_HARD);
		Mockito.doReturn(true).when(response).isError();

		CloudUser cloudUser = createCloudUser();
		String instanceId = FAKE_INSTANCE_ID;

		ComputeOrder computeOrder = new ComputeOrder();
		computeOrder.setInstanceId(instanceId);

		// exercise
		this.plugin.deleteInstance(computeOrder, cloudUser);

		// verify
		PowerMockito.verifyStatic(OpenNebulaClientUtil.class, VerificationModeFactory.times(1));
		OpenNebulaClientUtil.createClient(Mockito.anyString(), Mockito.anyString());

		PowerMockito.verifyStatic(OpenNebulaClientUtil.class, VerificationModeFactory.times(1));
		OpenNebulaClientUtil.getVirtualMachine(Mockito.eq(client), Mockito.anyString());

		Mockito.verify(virtualMachine, Mockito.times(1)).terminate(Mockito.eq(OpenNebulaComputePlugin.SHUTS_DOWN_HARD));
		Mockito.verify(response, Mockito.times(1)).isError();
	}
	
	// Test case: When calling the deleteInstance method, if the removal call is not
	// answered an error response is returned.
	@Test
	public void testDeleteInstanceUnsuccessful() throws FogbowException {
		// set up
		Client client = Mockito.mock(Client.class);
		PowerMockito.mockStatic(OpenNebulaClientUtil.class);
		BDDMockito.given(OpenNebulaClientUtil.createClient(Mockito.anyString(), Mockito.anyString()))
				.willReturn(client);

		VirtualMachine virtualMachine = Mockito.mock(VirtualMachine.class);
		BDDMockito.given(OpenNebulaClientUtil.getVirtualMachine(Mockito.eq(client), Mockito.anyString()))
				.willReturn(virtualMachine);

		OneResponse response = Mockito.mock(OneResponse.class);
		Mockito.doReturn(response).when(virtualMachine).terminate(OpenNebulaComputePlugin.SHUTS_DOWN_HARD);
		Mockito.doReturn(false).when(response).isError();

		CloudUser cloudUser = createCloudUser();
		String instanceId = FAKE_INSTANCE_ID;

		ComputeOrder computeOrder = new ComputeOrder();
		computeOrder.setInstanceId(instanceId);

		// exercise
		this.plugin.deleteInstance(computeOrder, cloudUser);

		// verify
		PowerMockito.verifyStatic(OpenNebulaClientUtil.class, VerificationModeFactory.times(1));
		OpenNebulaClientUtil.createClient(Mockito.anyString(), Mockito.anyString());

		PowerMockito.verifyStatic(OpenNebulaClientUtil.class, VerificationModeFactory.times(1));
		OpenNebulaClientUtil.getVirtualMachine(Mockito.eq(client), Mockito.anyString());

		Mockito.verify(virtualMachine, Mockito.times(1)).terminate(Mockito.eq(OpenNebulaComputePlugin.SHUTS_DOWN_HARD));
		Mockito.verify(response, Mockito.times(1)).isError();
	}
	
	private Template mockTemplatePoolIterator() throws UnexpectedException {
		TemplatePool templatePool = Mockito.mock(TemplatePool.class);
		BDDMockito.given(OpenNebulaClientUtil.getTemplatePool(Mockito.any(Client.class))).willReturn(templatePool);

		Template template = Mockito.mock(Template.class);
		Iterator<Template> templateIterator = Mockito.mock(Iterator.class);
		Mockito.when(templateIterator.hasNext()).thenReturn(true, false);
		Mockito.when(templateIterator.next()).thenReturn(template);
		Mockito.when(templatePool.iterator()).thenReturn(templateIterator);
		Mockito.when(template.getId()).thenReturn(FAKE_ID);
		Mockito.when(template.getName()).thenReturn(FLAVOR_KIND_NAME);
		Mockito.when(template.xpath(TEMPLATE_CPU_PATH)).thenReturn(TEMPLATE_CPU_VALUE);
		Mockito.when(template.xpath(TEMPLATE_MEMORY_PATH)).thenReturn(TEMPLATE_MEMORY_VALUE);
		return template;
	}

	private ComputeInstance CreateComputeInstance() {
		int cpu = CPU_VALUE_4;
		int ram = MEMORY_VALUE_2048;
		int disk = DISK_VALUE_8GB;
		
		String id = FAKE_INSTANCE_ID;
		String hostName = FAKE_HOST_NAME;
		String image = FAKE_IMAGE;
		String publicKey = FAKE_PUBLIC_KEY;
		List<UserData> userData = FAKE_LIST_USER_DATA;
		
		List<String> ipAddresses = null;
		
		ComputeInstance computeInstance = new ComputeInstance(
				id,
				OpenNebulaStateMapper.COMPUTE_RUNNING_STATE,
				hostName, 
				cpu, 
				ram, 
				disk, 
				ipAddresses, 
				image, 
				publicKey,
				userData);

		
		return computeInstance;
	}
	
	private HardwareRequirements createHardwareRequirements() {
		String id = FAKE_ID;
		String name = FLAVOR_KIND_NAME;
		
		int cpu = CPU_VALUE_4;
		int ram = MEMORY_VALUE_2048;
		int disk = DISK_VALUE_8GB;
		
		return new HardwareRequirements(name, id, cpu, ram, disk);
	}

	private List<String> listNetworkIds() {
		List<String> networksId = new ArrayList<>();
		networksId.addAll(this.computeOrder.getNetworkIds());
		return networksId;
	}
	
	private ComputeOrder createComputeOrder(List<String> networksId, int ...values) {
		int cpu = values[0];
		int memory = values[1];
		int disk = values[2];
		
		String imageId = FAKE_IMAGE_ID;
		String name = null, providingMember = null, requestingMember = null, cloudName = null;
		String publicKey = FAKE_PUBLIC_KEY;
		
		SystemUser systemUser = null;
		ArrayList<UserData> userData = FAKE_LIST_USER_DATA;
		
		ComputeOrder computeOrder = new ComputeOrder(
				systemUser,
				requestingMember, 
				providingMember,
				cloudName,
				name, 
				cpu, 
				memory, 
				disk, 
				imageId,
				userData,
				publicKey, 
				networksId);
		
		return computeOrder;
	}
	
	private CloudUser createCloudUser() {
		String userId = FAKE_ID;
		String userName = FAKE_NAME;
		String tokenValue = LOCAL_TOKEN_VALUE;

		return new CloudUser(userId, userName, tokenValue);
	}
	
	private String generateTemplate(int choice, String ...args) {
		String cpu;
		String memory;
		String defaultNetworkId;
		String privateNetworkId;
		String size;
		String template;
		
		switch (choice) {
		case 0:
			cpu = args[0];
			memory = args[1];
			defaultNetworkId = args[2] != null ? args[2] : String.valueOf(DEFAULT_NETWORK_ID);
			privateNetworkId = args[3];
			size = args[4];
			template = getTemplateWithTwoNetworkIds();
			return String.format(template, cpu, memory, defaultNetworkId, privateNetworkId, size);
			
		case 1:
			cpu = args[0];
			memory = args[1];
			defaultNetworkId = args[2] != null ? args[2] : String.valueOf(DEFAULT_NETWORK_ID);
			size = args[3];
			template = getTemplateWithOneNetworkId();
			return String.format(template, cpu, memory, defaultNetworkId, size);
			
		case 2:
			cpu = args[0];
			memory = args[1];
			defaultNetworkId = args[2] != null ? args[2] : String.valueOf(DEFAULT_NETWORK_ID);
			size = args[3];
			template = getTemplateWithVolatileDisk();
			return String.format(template, cpu, memory, defaultNetworkId, size);

		default:
			return null;
		}
	}

	private String getTemplateWithVolatileDisk() {
		String template = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" 
				+ "<TEMPLATE>\n"
				+ "    <CONTEXT>\n" 
				+ "        <USERDATA_ENCODING>base64</USERDATA_ENCODING>\n"
				+ "        <NETWORK>YES</NETWORK>\n"
				+ "        <USERDATA>fake-user-data</USERDATA>\n"
				+ "    </CONTEXT>\n" 
				+ "    <CPU>%s</CPU>\n" 
				+ "    <DISK>\n"
				+ "        <FORMAT>ext3</FORMAT>\n"
				+ "        <SIZE>6144</SIZE>\n"
				+ "        <TYPE>fs</TYPE>\n"
				+ "    </DISK>\n"
				+ "    <GRAPHICS>\n" 
				+ "        <LISTEN>0.0.0.0</LISTEN>\n" 
				+ "        <TYPE>vnc</TYPE>\n"
				+ "    </GRAPHICS>\n"
				+ "    <MEMORY>%s</MEMORY>\n"
				+ "    <NIC>\n"
				+ "        <NETWORK_ID>%s</NETWORK_ID>\n"
				+ "    </NIC>\n"
				+ "    <OS>\n" 
				+ "        <ARCH>x86_64</ARCH>\n"
				+ "    </OS>\n"
				+ "</TEMPLATE>\n";
		
		return template;
	}
	
	private String getTemplateWithTwoNetworkIds() {
		String template = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n" 
				+ "<TEMPLATE>\n"
				+ "    <CONTEXT>\n" 
				+ "        <USERDATA_ENCODING>base64</USERDATA_ENCODING>\n"
				+ "        <NETWORK>YES</NETWORK>\n"
				+ "        <USERDATA>fake-user-data</USERDATA>\n"
				+ "    </CONTEXT>\n" 
				+ "    <CPU>%s</CPU>\n" 
				+ "    <DISK>\n"
				+ "        <IMAGE_ID>fake-image-id</IMAGE_ID>\n" 
				+ "    </DISK>\n"
				+ "    <GRAPHICS>\n" 
				+ "        <LISTEN>0.0.0.0</LISTEN>\n" 
				+ "        <TYPE>vnc</TYPE>\n"
				+ "    </GRAPHICS>\n"
				+ "    <MEMORY>%s</MEMORY>\n"
				+ "    <NIC>\n"
				+ "        <NETWORK_ID>%s</NETWORK_ID>\n"
				+ "    </NIC>\n"
				+ "    <NIC>\n"
				+ "        <NETWORK_ID>%s</NETWORK_ID>\n"
				+ "    </NIC>\n"
				+ "    <OS>\n" 
				+ "        <ARCH>x86_64</ARCH>\n"
				+ "    </OS>\n"
				+ "</TEMPLATE>\n";
		
		return template;
	}
	
	private String getTemplateWithOneNetworkId() {
		String template = "<?xml version=\"1.0\" encoding=\"UTF-8\" standalone=\"yes\"?>\n"
				+ "<TEMPLATE>\n"
				+ "    <CONTEXT>\n"
				+ "        <USERDATA_ENCODING>base64</USERDATA_ENCODING>\n" 
				+ "        <NETWORK>YES</NETWORK>\n"
				+ "        <USERDATA>fake-user-data</USERDATA>\n"
				+ "    </CONTEXT>\n"
				+ "    <CPU>%s</CPU>\n"
				+ "    <DISK>\n"
				+ "        <IMAGE_ID>fake-image-id</IMAGE_ID>\n"
				+ "    </DISK>\n"
				+ "    <GRAPHICS>\n" 
				+ "        <LISTEN>0.0.0.0</LISTEN>\n"
				+ "        <TYPE>vnc</TYPE>\n"
				+ "    </GRAPHICS>\n"
				+ "    <MEMORY>%s</MEMORY>\n"
				+ "    <NIC>\n"
				+ "        <NETWORK_ID>%s</NETWORK_ID>\n"
				+ "    </NIC>\n"
				+ "    <OS>\n" 
				+ "        <ARCH>x86_64</ARCH>\n"
				+ "    </OS>\n"
				+ "</TEMPLATE>\n";
		
		return template;
	}
	
	private String getVirtualMachineResponse() {
		String xml = "<VM>\n"
    			+ "  <ID>fake-id</ID>\n"
    			+ "  <NAME>fake-name</NAME>\n"
    			+ "  <LCM_STATE>3</LCM_STATE>\n"
    			+ "  <TEMPLATE>\n"
    			+ "    <CPU>1</CPU>\n"
    			+ "    <DISK>\n"
    			+ "      <SIZE>8192</SIZE>\n"
    			+ "    </DISK>\n"
    			+ "    <MEMORY>1024</MEMORY>\n"
    			+ "    <NIC>\n"
    			+ "      <IP>172.16.100.201</IP>\n"
    			+ "    </NIC>\n"
    			+ "    <NIC>\n"
    			+ "      <IP>172.16.100.202</IP>\n"
    			+ "    </NIC>\n"
    			+ "  </TEMPLATE>\n"
    			+ "</VM>";
		
		return xml;
	}

	private ComputeOrder getComputeOrder() {
		ComputeOrder computeOrder = this.testUtils.createLocalComputeOrder();
		return computeOrder;
	}

	private CreateComputeRequest getCreateComputeRequest() {
		return this.plugin.createComputeRequest(
				this.computeOrder.getName(), NETWORK_CONFIRMATION_CONTEXT, this.computeOrder.getPublicKey(), USER_NAME,
				FAKE_BASE64_SCRIPT, String.valueOf(this.hardwareRequirements.getCpu()), DEFAULT_GRAPHIC_ADDRESS,
				DEFAULT_GRAPHIC_TYPE, this.computeOrder.getImageId(), String.valueOf(this.hardwareRequirements.getDisk()),
				String.valueOf(this.hardwareRequirements.getMemory()), this.networkIds, DEFAULT_ARCHITECTURE);
	}
}
