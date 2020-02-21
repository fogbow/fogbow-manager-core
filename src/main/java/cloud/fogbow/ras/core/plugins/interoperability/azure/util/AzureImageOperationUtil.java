package cloud.fogbow.ras.core.plugins.interoperability.azure.util;

import cloud.fogbow.ras.api.http.response.ImageSummary;
import cloud.fogbow.ras.core.plugins.interoperability.azure.compute.sdk.model.AzureGetImageRef;
import org.apache.commons.lang3.StringUtils;

public class AzureImageOperationUtil {

    static final String IMAGE_SUMMARY_ID_SEPARATOR = "@#";
    static final String IMAGE_SUMMARY_NAME_SEPARATOR = " - ";

    private static final int SUMMARY_ID_PARAMETER_SIZE = 3;
    private static final int PUBLISHER_ID_SUMMARY_POSITION = 0;
    private static final int OFFER_ID_SUMMARY_POSITION = 1;
    private static final int SKU_ID_SUMMARY_POSITION = 2;

    private static final int SUMMARY_NAME_PARAMETER_SIZE = 2;
    private static final int OFFER_NAME_SUMMARY_POSITION = 0;
    private static final int SKU_NAME_SUMMARY_POSITION = 1;

    static ImageSummary buildImageSummaryBy(AzureGetImageRef azureVirtualMachineImage) {
        String id = convertToImageSummaryId(azureVirtualMachineImage);
        String name = convertToImageSummaryName(azureVirtualMachineImage);
        return new ImageSummary(id, name);
    }

    public static AzureGetImageRef buildAzureVirtualMachineImageBy(String imageSummaryId) {
        String[] imageSummaryIdChunks = imageSummaryId.split(IMAGE_SUMMARY_ID_SEPARATOR);
        String published = imageSummaryIdChunks[PUBLISHER_ID_SUMMARY_POSITION];
        String offer = imageSummaryIdChunks[OFFER_ID_SUMMARY_POSITION];
        String sku = imageSummaryIdChunks[SKU_ID_SUMMARY_POSITION];
        return new AzureGetImageRef(published, offer, sku);
    }

    static String convertToImageSummaryId(AzureGetImageRef azureGetImageRef) {
        String[] list = new String[SUMMARY_ID_PARAMETER_SIZE];
        list[PUBLISHER_ID_SUMMARY_POSITION] = azureGetImageRef.getPublisher();
        list[OFFER_ID_SUMMARY_POSITION] = azureGetImageRef.getOffer();
        list[SKU_ID_SUMMARY_POSITION] = azureGetImageRef.getSku();
        return StringUtils.join(list, IMAGE_SUMMARY_ID_SEPARATOR);
    }

    static String convertToImageSummaryName(AzureGetImageRef azureGetImageRef) {
        String[] list = new String[SUMMARY_NAME_PARAMETER_SIZE];
        list[OFFER_NAME_SUMMARY_POSITION] = azureGetImageRef.getOffer();
        list[SKU_NAME_SUMMARY_POSITION] = azureGetImageRef.getSku();
        return StringUtils.join(list, IMAGE_SUMMARY_NAME_SEPARATOR);
    }

}
