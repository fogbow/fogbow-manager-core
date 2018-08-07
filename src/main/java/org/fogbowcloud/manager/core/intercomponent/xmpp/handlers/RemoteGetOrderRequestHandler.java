package org.fogbowcloud.manager.core.intercomponent.xmpp.handlers;

import org.dom4j.Element;
import org.fogbowcloud.manager.core.intercomponent.RemoteFacade;
import org.fogbowcloud.manager.core.intercomponent.xmpp.XmppExceptionToErrorConditionTranslator;
import org.fogbowcloud.manager.core.intercomponent.xmpp.IqElement;
import org.fogbowcloud.manager.core.intercomponent.xmpp.RemoteMethod;
import org.fogbowcloud.manager.core.models.ResourceType;
import org.fogbowcloud.manager.core.models.instances.Instance;
import org.fogbowcloud.manager.core.models.tokens.FederationUser;
import org.jamppa.component.handler.AbstractQueryHandler;
import org.xmpp.packet.IQ;

import com.google.gson.Gson;

public class RemoteGetOrderRequestHandler extends AbstractQueryHandler {

    public static final String REMOTE_GET_INSTANCE = RemoteMethod.REMOTE_GET_ORDER.toString();

    public RemoteGetOrderRequestHandler() {
        super(REMOTE_GET_INSTANCE);
    }

    @Override
    public IQ handle(IQ iq) {
        String orderId = unMarshallOrderId(iq);
        ResourceType resourceType = unMarshallResourceType(iq);
        FederationUser federationUser = unMarshallFederationUser(iq);

        IQ response = IQ.createResultIQ(iq);

        try {
            Instance instance = RemoteFacade.getInstance().getResourceInstance(orderId, federationUser, resourceType);
            Element instanceElement = marsallInstance(response, instance);
            instanceElement.setText(new Gson().toJson(instance));
        } catch (Exception e) {
            XmppExceptionToErrorConditionTranslator.updateErrorCondition(response, e);
        }
        return response;
    }

	private Element marsallInstance(IQ response, Instance instance) {
		Element queryElement = response.getElement().addElement(IqElement.QUERY.toString(), REMOTE_GET_INSTANCE);
		Element instanceElement = queryElement.addElement(IqElement.INSTANCE.toString());
		
		Element instanceClassNameElement = queryElement.addElement(IqElement.INSTANCE_CLASS_NAME.toString());
		instanceClassNameElement.setText(instance.getClass().getName());
		return instanceElement;
	}

	private FederationUser unMarshallFederationUser(IQ iq) {
		Element federationUserElement = iq.getElement().element(IqElement.FEDERATION_USER.toString());
        FederationUser federationUser = new Gson().fromJson(federationUserElement.getText(), FederationUser.class);
		return federationUser;
	}

	private ResourceType unMarshallResourceType(IQ iq) {
		Element queryElement = iq.getElement().element(IqElement.QUERY.toString());
		Element orderTypeElementRequest = queryElement.element(IqElement.INSTANCE_TYPE.toString());
        ResourceType resourceType = new Gson().fromJson(orderTypeElementRequest.getText(), ResourceType.class);
		return resourceType;
	}

	private String unMarshallOrderId(IQ iq) {
		Element queryElement = iq.getElement().element(IqElement.QUERY.toString());
		Element orderIdElement = queryElement.element(IqElement.ORDER_ID.toString());
        String orderId = orderIdElement.getText();
		return orderId;
	}
}
