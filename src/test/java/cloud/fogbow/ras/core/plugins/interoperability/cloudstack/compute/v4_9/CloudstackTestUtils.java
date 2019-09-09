package cloud.fogbow.ras.core.plugins.interoperability.cloudstack.compute.v4_9;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class CloudstackTestUtils {

    private static final String LIST_SERVICE_OFFERINGS_RESPONSE = "listserviceofferingsresponse.json";
    private static final String LIST_DISK_OFFERINGS_RESPONSE = "listdiskofferingsresponse.json";
    private static final String DEPLOY_VIRTUAL_MACHINE_RESPONSE = "deployvirtualmachineresponse.json";
    private static final String LIST_VIRTUAL_MACHINE_RESPONSE = "listvirtualmachinesresponse.json";
    private static final String NIC_VIRTUAL_MACHINE_RESPONSE = "nic.json";
    private static final String LIST_VOLUMES_RESPONSE = "listvolumesresponse.json";

    private static final String CLOUDSTACK_RESOURCE_PATH = "cloud" + File.separator +
            "plugins" + File.separator + "interoperability" + File.separator +
            "cloudstack" + File.separator;

    protected static final String AND_OPERATION_URL_PARAMETER = "&";

    protected static final String CLOUDSTACK_URL_DEFAULT = "http://localhost";

    static String createGetAllServiceOfferingsResponseJson(
            String id, String name, int cpuNumber, int memory, String tags) throws IOException {

        String rawJson = readFileAsString(getPathCloudstackFile() + LIST_SERVICE_OFFERINGS_RESPONSE);

        return String.format(rawJson, id, name, cpuNumber, memory, tags);
    }

    static String createGetAllDiskOfferingsResponseJson(
            String id, int disk, boolean customized, String tags) throws IOException {

        String rawJson = readFileAsString(getPathCloudstackFile() + LIST_DISK_OFFERINGS_RESPONSE);

        return String.format(rawJson, id, disk, customized, tags);
    }

    static String createDeployVirtualMachineResponseJson(String id) throws IOException {
        String rawJson = readFileAsString(getPathCloudstackFile() + DEPLOY_VIRTUAL_MACHINE_RESPONSE);

        return String.format(rawJson, id);
    }

    static String createGetVolumesResponseJson(
            String id, String name, double size, String state) throws IOException {

        String rawJson = readFileAsString(getPathCloudstackFile() + LIST_VOLUMES_RESPONSE);

        return String.format(rawJson, id, name, size, state);
    }

    static String createGetVirtualMachineResponseJson(
            String id, String name, String state, int memory,
            int cpuNumber, List<GetVirtualMachineResponse.Nic> nics) throws IOException {

        String rawJson = readFileAsString(getPathCloudstackFile() + LIST_VIRTUAL_MACHINE_RESPONSE);

        ArrayList<String> nicsStrs = new ArrayList<>();
        nics.stream().forEach(nic -> {
            try {
                nicsStrs.add(createNicJson(nic.getIpAddress()));
            } catch (IOException e) {
                throw new Error();
            }
        });
        String[] nicsStrsArr = new String[nicsStrs.size()];
        nicsStrs.toArray(nicsStrsArr);
        String nicsFullStr = String.join(",", nicsStrsArr);

        return String.format(rawJson, id, name, state, memory, cpuNumber, nicsFullStr);
    }

    static String createNicJson(String idAddress) throws IOException {
        String rawJson = readFileAsString(getPathCloudstackFile() + NIC_VIRTUAL_MACHINE_RESPONSE);

        return String.format(rawJson, idAddress);
    }

    private static String readFileAsString(final String fileName) throws IOException {
        Path path = Paths.get(fileName);
        byte[] bytes = Files.readAllBytes(path);
        String data = new String(bytes);
        return data;
    }

    private static String getPathCloudstackFile() {
        String rootPath = Thread.currentThread().getContextClassLoader().getResource("").getPath();
        return rootPath + CLOUDSTACK_RESOURCE_PATH;
    }
}