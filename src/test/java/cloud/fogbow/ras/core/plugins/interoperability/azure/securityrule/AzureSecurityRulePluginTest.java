package cloud.fogbow.ras.core.plugins.interoperability.azure.securityrule;

import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.InvalidParameterException;
import cloud.fogbow.common.exceptions.UnexpectedException;
import cloud.fogbow.common.models.AzureUser;
import cloud.fogbow.common.util.HomeDir;
import cloud.fogbow.ras.api.http.response.SecurityRuleInstance;
import cloud.fogbow.ras.api.parameters.SecurityRule;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.constants.SystemConstants;
import cloud.fogbow.ras.core.TestUtils;
import cloud.fogbow.ras.core.models.ResourceType;
import cloud.fogbow.ras.core.models.orders.NetworkOrder;
import cloud.fogbow.ras.core.models.orders.Order;
import cloud.fogbow.ras.core.models.orders.PublicIpOrder;
import cloud.fogbow.ras.core.models.orders.VolumeOrder;
import cloud.fogbow.ras.core.plugins.interoperability.azure.AzureTestUtils;
import cloud.fogbow.ras.core.plugins.interoperability.azure.securityrule.sdk.AzureNetworkSecurityGroupOperationSDK;
import cloud.fogbow.ras.core.plugins.interoperability.azure.securityrule.sdk.model.AzureUpdateNetworkSecurityGroupRef;
import cloud.fogbow.ras.core.plugins.interoperability.azure.securityrule.util.SecurityRuleIdContext;
import cloud.fogbow.ras.core.plugins.interoperability.azure.util.AzureGeneralUtil;
import cloud.fogbow.ras.core.plugins.interoperability.azure.util.AzureResourceIdBuilder;
import com.microsoft.azure.management.Azure;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.powermock.api.mockito.PowerMockito;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

@RunWith(PowerMockRunner.class)
@PrepareForTest({
        AzureGeneralUtil.class,
        SecurityRuleIdContext.class
})
public class AzureSecurityRulePluginTest extends TestUtils {
    private static final int DEFAULT_PORT_FROM = 22;
    private static final int DEFAULT_PORT_TO = 22;
    private AzureSecurityRulePlugin plugin;
    private Azure azure;
    private AzureUser azureUser;
    private AzureNetworkSecurityGroupOperationSDK operation;

    @Rule
    public ExpectedException expectedException = ExpectedException.none();

    @Before
    public void setUp() {
        String azureConfFilePath = HomeDir.getPath() +
                SystemConstants.CLOUDS_CONFIGURATION_DIRECTORY_NAME + File.separator
                + AzureTestUtils.AZURE_CLOUD_NAME + File.separator
                + SystemConstants.CLOUD_SPECIFICITY_CONF_FILE_NAME;
        this.plugin = Mockito.spy(new AzureSecurityRulePlugin(azureConfFilePath));
        this.operation = Mockito.mock(AzureNetworkSecurityGroupOperationSDK.class);
        this.plugin.setAzureNetworkSecurityGroupOperationSDK(operation);
        this.azureUser = AzureTestUtils.createAzureUser();
        this.azure = null;
    }

    // test case: When calling the requestSecurityRule method with mocked methods,
    // it must verify if it creates all variable correct.
    @Test
    public void testRequestSecurityRuleSuccessfully() throws FogbowException {
        // set up
        PowerMockito.mockStatic(AzureGeneralUtil.class);
        PowerMockito.mockStatic(SecurityRuleIdContext.class);
        Order order = Mockito.mock(NetworkOrder.class);
        SecurityRule securityRule = createSecurityRule();

        String majorOrderInstanceId = TestUtils.FAKE_INSTANCE_ID;
        String expectedSecurityGroupName = "security-group-name";

        Mockito.when(order.getInstanceId()).thenReturn(majorOrderInstanceId);
        Mockito.doNothing().when(this.plugin).checkOrderType(Mockito.eq(order));

        Mockito.when(AzureGeneralUtil.defineInstanceId(Mockito.eq(majorOrderInstanceId)))
                .thenReturn(expectedSecurityGroupName);

        String expectedSecurityGroupId = AzureResourceIdBuilder.networkSecurityGroupId()
                .withSubscriptionId(azureUser.getSubscriptionId())
                .withResourceGroupName(AzureTestUtils.DEFAULT_RESOURCE_GROUP_NAME)
                .withResourceName(expectedSecurityGroupName)
                .build();

        String securityRuleName = AzureTestUtils.RESOURCE_NAME;
        Mockito.when(AzureGeneralUtil.generateResourceName()).thenReturn(securityRuleName);

        AzureUpdateNetworkSecurityGroupRef azureUpdateNetworkSecurityRef = AzureUpdateNetworkSecurityGroupRef.builder()
                .ruleResourceName(securityRuleName)
                .networkSecurityGroupId(expectedSecurityGroupId)
                .protocol(securityRule.getProtocol())
                .cidr(securityRule.getCidr())
                .direction(securityRule.getDirection())
                .portFrom(securityRule.getPortFrom())
                .portTo(securityRule.getPortTo())
                .checkAndBuild();

        Mockito.doNothing().when(this.operation).doCreateInstance(Mockito.eq(azureUpdateNetworkSecurityRef),
                Mockito.eq(azureUser));

        String expectedInstanceId = TestUtils.FAKE_INSTANCE_ID;
        Mockito.when(SecurityRuleIdContext.buildInstanceId(Mockito.eq(expectedSecurityGroupName),
                Mockito.eq(securityRuleName))).thenReturn(expectedInstanceId);

        // exercise
        String instanceId = this.plugin.requestSecurityRule(securityRule, order, azureUser);

        // verify
        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE)).checkOrderType(Mockito.eq(order));

        PowerMockito.verifyStatic(AzureGeneralUtil.class, Mockito.times(TestUtils.RUN_ONCE));
        AzureGeneralUtil.generateResourceName();

        PowerMockito.verifyStatic(AzureGeneralUtil.class, Mockito.times(TestUtils.RUN_ONCE));
        AzureGeneralUtil.defineInstanceId(Mockito.eq(majorOrderInstanceId));

        Assert.assertEquals(expectedInstanceId, instanceId);
    }

    // test case: When calling the getSecurityRules method with mocked methods,
    // it must verify if it creates all variable correct.
    @Test
    public void testGetSecurityRules() throws FogbowException {
        // set up
        Order majorOrder = Mockito.mock(NetworkOrder.class);
        List<SecurityRuleInstance> securityRuleInstanceList = new ArrayList<>();

        Mockito.doReturn(securityRuleInstanceList).when(this.plugin).doGetSecurityRules(Mockito.eq(majorOrder),
                Mockito.eq(azureUser));

        // exercise
        this.plugin.getSecurityRules(majorOrder, azureUser);

        // verify
        Mockito.verify(this.plugin, Mockito.times(TestUtils.RUN_ONCE)).doGetSecurityRules(Mockito.eq(majorOrder),
                Mockito.eq(azureUser));
    }

    // test case: When calling the deleteSecurityRule method with mocked methods,
    // it must verify if it creates all variable correct.
    @Test
    public void testDeleteSecurityRuleNetwork() throws FogbowException {
        // set up
        String securityRuleId = "security-rule-id";
        String securityRuleName = "security-rule-name";
        String networkSecurityGroupName = "network-security-group-name";

        SecurityRuleIdContext mockedSecurityRuleIdContext = Mockito.mock(SecurityRuleIdContext.class);
        Mockito.when(mockedSecurityRuleIdContext.getNetworkSecurityGroupName()).thenReturn(networkSecurityGroupName);
        Mockito.when(mockedSecurityRuleIdContext.getSecurityRuleName()).thenReturn(securityRuleName);

        Mockito.when(this.plugin.getSecurityRuleIdContext(securityRuleId)).thenReturn(mockedSecurityRuleIdContext);

        String networkSecurityGroupId = AzureResourceIdBuilder.networkSecurityGroupId()
                .withSubscriptionId(azureUser.getSubscriptionId())
                .withResourceGroupName(AzureTestUtils.DEFAULT_RESOURCE_GROUP_NAME)
                .withResourceName(networkSecurityGroupName)
                .build();

        Mockito.doNothing().when(this.operation).deleteNetworkSecurityRule(
                Mockito.eq(networkSecurityGroupId), Mockito.eq(securityRuleName), Mockito.eq(azureUser));

        // exercise
        this.plugin.deleteSecurityRule(securityRuleId, azureUser);

        // verify
        Mockito.verify(this.operation, Mockito.times(TestUtils.RUN_ONCE)).deleteNetworkSecurityRule(
                Mockito.eq(networkSecurityGroupId), Mockito.eq(securityRuleName), Mockito.eq(azureUser));
        Mockito.verify(mockedSecurityRuleIdContext, Mockito.times(TestUtils.RUN_ONCE)).getNetworkSecurityGroupName();
        Mockito.verify(mockedSecurityRuleIdContext, Mockito.times(TestUtils.RUN_ONCE)).getSecurityRuleName();
    }

    // test case: When calling the doGetSecurityRules method with mocked methods,
    // it must verify if it creates all variable correct.
    @Test
    public void testDoGetSecurityRulesSuccessfully() throws FogbowException {
        // set up
        PowerMockito.mockStatic(AzureGeneralUtil.class);
        Order order = Mockito.mock(NetworkOrder.class);
        Mockito.when(order.getInstanceId()).thenReturn(FAKE_INSTANCE_ID);
        Mockito.when(order.getType()).thenReturn(ResourceType.NETWORK);

        String networkSecurityGroupName = "network-security-group-name";
        Mockito.when(AzureGeneralUtil.defineResourceName(Mockito.eq(order.getInstanceId())))
                .thenReturn(networkSecurityGroupName);

        String networkSecurityGroupId = AzureResourceIdBuilder.networkSecurityGroupId()
                .withSubscriptionId(azureUser.getSubscriptionId())
                .withResourceGroupName(AzureTestUtils.DEFAULT_RESOURCE_GROUP_NAME)
                .withResourceName(networkSecurityGroupName)
                .build();

        Mockito.when(this.operation.getNetworkSecurityRules(Mockito.eq(networkSecurityGroupId), Mockito.eq(azureUser)))
                .thenReturn(new ArrayList<>());

        // exercise
        this.plugin.doGetSecurityRules(order, azureUser);

        // verify
        Mockito.verify(this.operation, Mockito.times(TestUtils.RUN_ONCE)).getNetworkSecurityRules(
                Mockito.eq(networkSecurityGroupId), Mockito.eq(azureUser));

        PowerMockito.verifyStatic(AzureGeneralUtil.class, Mockito.times(TestUtils.RUN_ONCE));
        AzureGeneralUtil.defineResourceName(Mockito.eq(order.getInstanceId()));
    }

    // test case: When calling the doGetSecurityRules method with mocked methods
    // and the order has a type different of network or public ip, it must throw a exception
    @Test
    public void testDoGetSecurityRulesInvalidResourceType() throws FogbowException {
        // set up
        Order order = Mockito.mock(VolumeOrder.class);
        Mockito.when(order.getType()).thenReturn(ResourceType.VOLUME);

        String errorMsg = String.format(Messages.Error.INVALID_LIST_SECURITY_RULE_TYPE, order.getType());

        // verify
        this.expectedException.expect(UnexpectedException.class);
        this.expectedException.expectMessage(errorMsg);

        // exercise
        this.plugin.doGetSecurityRules(order, azureUser);
    }

    // test case: When calling the checkOrderType method with mocked methods
    // and the order is a NetworkOrder it must pass without any error
    @Test
    public void testCheckOrderTypeSuccessfullyNetwork() throws FogbowException {
        // set up
        Order order = Mockito.mock(NetworkOrder.class);
        Mockito.when(order.getType()).thenReturn(ResourceType.NETWORK);

        // exercise
        this.plugin.checkOrderType(order);
    }

    // test case: When calling the checkOrderType method with mocked methods
    // and the order is a NetworkOrder it must pass without any error
    @Test
    public void testCheckOrderTypeSuccessfullyPublicIp() throws FogbowException {
        // set up
        Order order = Mockito.mock(PublicIpOrder.class);
        Mockito.when(order.getType()).thenReturn(ResourceType.PUBLIC_IP);

        // exercise
        this.plugin.checkOrderType(order);
    }

    // test case: When calling the checkOrderType method with mocked methods
    // and the order has a type different of network or public ip, it must throw a exception
    @Test
    public void testCheckOrderTypeInvalidResourceType() throws FogbowException {
        // set up
        Order order = Mockito.mock(VolumeOrder.class);
        Mockito.when(order.getType()).thenReturn(ResourceType.VOLUME);

        // verify
        this.expectedException.expect(InvalidParameterException.class);
        this.expectedException.expectMessage(Messages.Exception.INVALID_RESOURCE);

        // exercise
        this.plugin.checkOrderType(order);
    }

    private SecurityRule createSecurityRule() {
        SecurityRule securityRule = Mockito.mock(SecurityRule.class);

        String cidr = DEFAULT_CIDR;
        int portFrom = DEFAULT_PORT_FROM;
        int portTo = DEFAULT_PORT_TO;
        SecurityRule.Direction direction = null;
        SecurityRule.Protocol protocol = null;

        Mockito.when(securityRule.getCidr()).thenReturn(cidr);
        Mockito.when(securityRule.getPortFrom()).thenReturn(portFrom);
        Mockito.when(securityRule.getPortTo()).thenReturn(portTo);
        Mockito.when(securityRule.getDirection()).thenReturn(direction);
        Mockito.when(securityRule.getProtocol()).thenReturn(protocol);

        return securityRule;
    }
}
