package org.fogbowcloud.ras.core.plugins.interoperability.cloudstack.image;

import org.apache.http.client.HttpResponseException;
import org.fogbowcloud.ras.core.exceptions.FogbowRasException;
import org.fogbowcloud.ras.core.exceptions.InstanceNotFoundException;
import org.fogbowcloud.ras.core.exceptions.UnexpectedException;
import org.fogbowcloud.ras.core.models.images.Image;
import org.fogbowcloud.ras.core.models.tokens.CloudStackToken;
import org.fogbowcloud.ras.core.plugins.interoperability.ImagePlugin;
import org.fogbowcloud.ras.core.plugins.interoperability.cloudstack.CloudStackHttpToFogbowRasExceptionMapper;
import org.fogbowcloud.ras.core.plugins.interoperability.cloudstack.CloudStackUrlUtil;
import org.fogbowcloud.ras.util.PropertiesUtil;
import org.fogbowcloud.ras.util.connectivity.AuditableHttpRequestClient;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

public class CloudStackImagePlugin implements ImagePlugin<CloudStackToken> {

    public static final String CLOUDSTACK_URL = "cloudstack_api_url";

    private String cloudStackUrl;
    private AuditableHttpRequestClient client;
    private Properties properties;

    public CloudStackImagePlugin(String confFilePath) {
        this.properties = PropertiesUtil.readProperties(confFilePath);
        this.cloudStackUrl = this.properties.getProperty(CLOUDSTACK_URL);
        this.client = new AuditableHttpRequestClient();
    }

    @Override
    public Map<String, String> getAllImages(CloudStackToken cloudStackToken) throws FogbowRasException, UnexpectedException {
        GetAllImagesRequest request = new GetAllImagesRequest.Builder().build(this.cloudStackUrl);

        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudStackToken.getTokenValue());

        String jsonResponse = null;
        try {
            jsonResponse = this.client.doGetRequest(request.getUriBuilder().toString(), cloudStackToken);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowRasExceptionMapper.map(e);
        }

        GetAllImagesResponse response = GetAllImagesResponse.fromJson(jsonResponse);
        List<GetAllImagesResponse.Image> images = response.getImages();

        Map<String, String> idToImageNames = new HashMap<>();
        for (GetAllImagesResponse.Image image : images) {
            idToImageNames.put(image.getId(), image.getName());
        }

        return idToImageNames;
    }

    @Override
    public Image getImage(String imageId, CloudStackToken cloudStackToken) throws FogbowRasException, UnexpectedException {
        GetAllImagesRequest request = new GetAllImagesRequest.Builder()
                .id(imageId)
                .build(this.cloudStackUrl);

        CloudStackUrlUtil.sign(request.getUriBuilder(), cloudStackToken.getTokenValue());

        String jsonResponse = null;
        try {
            jsonResponse = this.client.doGetRequest(request.getUriBuilder().toString(), cloudStackToken);
        } catch (HttpResponseException e) {
            CloudStackHttpToFogbowRasExceptionMapper.map(e);
        }

        GetAllImagesResponse response = GetAllImagesResponse.fromJson(jsonResponse);
        List<GetAllImagesResponse.Image> images = response.getImages();

        if (images != null && images.size() > 0) {
            GetAllImagesResponse.Image image = images.get(0);
            return new Image(image.getId(), image.getName(), image.getSize(), -1, -1, null);
        } else {
            throw new InstanceNotFoundException();
        }
    }

    protected void setClient(AuditableHttpRequestClient client) {
        this.client = client;
    }
}
