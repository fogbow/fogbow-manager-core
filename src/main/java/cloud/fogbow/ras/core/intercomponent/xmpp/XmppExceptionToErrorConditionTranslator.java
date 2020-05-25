package cloud.fogbow.ras.core.intercomponent.xmpp;

import cloud.fogbow.common.exceptions.*;
import cloud.fogbow.ras.constants.Messages;
import org.apache.log4j.Logger;
import org.xmpp.packet.IQ;
import org.xmpp.packet.PacketError;

public class XmppExceptionToErrorConditionTranslator {
    private static final Logger LOGGER = Logger.getLogger(XmppExceptionToErrorConditionTranslator.class);

    public static void updateErrorCondition(IQ response, Throwable e) {
        // Exceptions other than FogbowException are possible bugs in the code, and should be logged in error level
        if (!(e instanceof FogbowException)) {
            LOGGER.error(e.getMessage(), e);
        }
        PacketError error = new PacketError(mapExceptionToCondition(e));
        if (e.getMessage() != null) {
            error.setText(e.getMessage());
        } else {
            error.setText(String.format(Messages.Error.UNEXPECTED_ERROR_WITH_MESSAGE, e.toString()));
        }
        response.setError(error);
    }

    private static PacketError.Condition mapExceptionToCondition(Throwable e) {
        if (e.getClass() == UnauthorizedRequestException.class) {
            return PacketError.Condition.forbidden;
        } else if (e.getClass() == UnauthenticatedUserException.class) {
            return PacketError.Condition.not_authorized;
        } else if (e.getClass() == InvalidParameterException.class) {
            return PacketError.Condition.bad_request;
        } else if (e.getClass() == InstanceNotFoundException.class) {
            return PacketError.Condition.item_not_found;
        } else if (e.getClass() == QuotaExceededException.class) {
            return PacketError.Condition.conflict;
        } else if (e.getClass() == NoAvailableResourcesException.class) {
            return PacketError.Condition.not_acceptable;
        } else if (e.getClass() == UnavailableProviderException.class) {
            return PacketError.Condition.remote_server_not_found;
        } else if (e.getClass() == OnGoingOperationException.class) {
            // TODO (chico) - This mapping condition must be re-examined.
            // We could create our own conditions.
            return PacketError.Condition.resource_constraint;
        } else if (e.getClass() == UnexpectedException.class) {
            return PacketError.Condition.internal_server_error;
        } else {
            return PacketError.Condition.undefined_condition;
        }
    }
}
