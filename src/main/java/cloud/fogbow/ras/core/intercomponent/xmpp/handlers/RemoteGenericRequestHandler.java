package cloud.fogbow.ras.core.intercomponent.xmpp.handlers;

import cloud.fogbow.common.exceptions.UnexpectedException;
import cloud.fogbow.common.models.SystemUser;
import cloud.fogbow.common.util.GsonHolder;
import cloud.fogbow.common.util.connectivity.FogbowGenericResponse;
import cloud.fogbow.ras.core.intercomponent.RemoteFacade;
import cloud.fogbow.ras.core.intercomponent.xmpp.IqElement;
import cloud.fogbow.ras.core.intercomponent.xmpp.RemoteMethod;
import cloud.fogbow.ras.core.intercomponent.xmpp.XmppExceptionToErrorConditionTranslator;
import cloud.fogbow.common.util.connectivity.FogbowGenericRequest;
import org.apache.log4j.Logger;
import org.dom4j.Element;
import org.jamppa.component.handler.AbstractQueryHandler;
import org.xmpp.packet.IQ;

public class RemoteGenericRequestHandler extends AbstractQueryHandler {
    private final Logger LOGGER = Logger.getLogger(RemoteGenericRequestHandler.class);

    private static final String REMOTE_GENERIC_REQUEST = RemoteMethod.REMOTE_GENERIC_REQUEST.toString();

    public RemoteGenericRequestHandler() {
        super(REMOTE_GENERIC_REQUEST);
    }

    @Override
    public IQ handle(IQ iq) {
        String cloudName = unmarshalCloudName(iq);
        IQ response = IQ.createResultIQ(iq);
        try {
            FogbowGenericRequest fogbowGenericRequest = unmarshalGenericRequest(iq);
            SystemUser systemUser = unmarshalFederationUser(iq);

            FogbowGenericResponse fogbowGenericResponse = RemoteFacade.getInstance().
                    genericRequest(iq.getFrom().toBareJID(), cloudName, fogbowGenericRequest, systemUser);
            updateResponse(response, fogbowGenericResponse);
        } catch (Exception e) {
            XmppExceptionToErrorConditionTranslator.updateErrorCondition(response, e);
        }

        return response;
    }

    private String unmarshalCloudName(IQ iq) {
        Element queryElement = iq.getElement().element(IqElement.QUERY.toString());

        Element cloudNameElement = queryElement.element(IqElement.CLOUD_NAME.toString());
        String cloudName = GsonHolder.getInstance().fromJson(cloudNameElement.getText(), String.class);
        return cloudName;
    }

    private SystemUser unmarshalFederationUser(IQ iq) {
        Element queryElement = iq.getElement().element(IqElement.QUERY.toString());
        Element federationUserElement = queryElement.element(IqElement.FEDERATION_USER.toString());
        SystemUser systemUser = GsonHolder.getInstance().fromJson(federationUserElement.getText(),
                SystemUser.class);
        return systemUser;
    }

    private FogbowGenericRequest unmarshalGenericRequest(IQ iq) throws UnexpectedException {
        Element queryElement = iq.getElement().element(IqElement.QUERY.toString());
        Element genericRequestElement = queryElement.element(IqElement.GENERIC_REQUEST.toString());
        Element genericRequestClassNameElement = queryElement.element(IqElement.GENERIC_REQUEST_CLASS_NAME.toString());

        try {
            return  (FogbowGenericRequest) GsonHolder.getInstance().fromJson(genericRequestElement.getText(),
                    Class.forName(genericRequestClassNameElement.getText()));
        } catch (ClassNotFoundException|ClassCastException e) {
            throw new UnexpectedException(e.getMessage(), e);
        }
    }

    private void updateResponse(IQ response, FogbowGenericResponse fogbowGenericResponse) {
        Element queryEl = response.getElement().addElement(IqElement.QUERY.toString(), REMOTE_GENERIC_REQUEST);
        Element genericRequestElement = queryEl.addElement(IqElement.GENERIC_REQUEST_RESPONSE.toString());
        Element genericRequestElementClassname = queryEl.addElement(IqElement.GENERIC_REQUEST_RESPONSE_CLASS_NAME.toString());

        genericRequestElement.setText(GsonHolder.getInstance().toJson(fogbowGenericResponse));
        genericRequestElementClassname.setText(fogbowGenericResponse.getClass().getName());
    }
}
