package cloud.fogbow.ras.core.plugins.mapper.one2one;

import cloud.fogbow.as.core.models.OneToOneMappableSystemUser;
import cloud.fogbow.common.exceptions.FogbowException;
import cloud.fogbow.common.exceptions.InvalidParameterException;
import cloud.fogbow.common.models.CloudUser;
import cloud.fogbow.common.models.SystemUser;
import cloud.fogbow.ras.constants.ConfigurationPropertyKeys;
import cloud.fogbow.ras.constants.Messages;
import cloud.fogbow.ras.core.PropertiesHolder;
import cloud.fogbow.ras.core.plugins.mapper.SystemToCloudMapperPlugin;

public abstract class GenericOneToOneSystemToCloudMapper implements SystemToCloudMapperPlugin<CloudUser> {
    private SystemToCloudMapperPlugin remoteMapper;
    private String memberId;

    public GenericOneToOneSystemToCloudMapper(SystemToCloudMapperPlugin remoteMapper) {
        this.remoteMapper = remoteMapper;
        this.memberId = PropertiesHolder.getInstance().getProperty(ConfigurationPropertyKeys.LOCAL_MEMBER_ID_KEY);
    }

    public abstract CloudUser localMap(OneToOneMappableSystemUser systemUser) throws InvalidParameterException;

    @Override
    public CloudUser map(SystemUser systemUser) throws FogbowException {
        if (!(systemUser instanceof OneToOneMappableSystemUser)) {
            throw new InvalidParameterException(Messages.Exception.UNABLE_TO_MAP_SYSTEM_USER);
        }
        if (systemUser.getIdentityProviderId().equals(this.memberId)) {
            // TODO: check if token hasn't expired; if it has, then renew it.
            return localMap((OneToOneMappableSystemUser) systemUser);
        } else {
            return remoteMapper.map(systemUser);
        }
    }

    public String getMemberId() {
        return this.memberId;
    }

    protected void setRemoteMapper(SystemToCloudMapperPlugin remoteMapper) {
        this.remoteMapper = remoteMapper;
    }
}
