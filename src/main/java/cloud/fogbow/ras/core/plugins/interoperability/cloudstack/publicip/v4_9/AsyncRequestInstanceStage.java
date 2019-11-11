package cloud.fogbow.ras.core.plugins.interoperability.cloudstack.publicip.v4_9;

public class AsyncRequestInstanceStage {

    private State state;
    private String currentJobId;
    private String ip;
    private String ipInstanceId;
    private String computeInstanceId;

    public AsyncRequestInstanceStage(State state, String currentJobId, String computeInstanceId) {
        this.state = state;
        this.currentJobId = currentJobId;
        this.computeInstanceId = computeInstanceId;
    }

    public State getState() {
        return state;
    }

    public String getCurrentJobId() {
        return currentJobId;
    }

    public String getComputeInstanceId() {
        return computeInstanceId;
    }

    public void setState(State state) {
        this.state = state;
    }

    public void setCurrentJobId(String currentJobId) {
        this.currentJobId = currentJobId;
    }

    public String getIp() {
        return ip;
    }

    public void setIp(String ip) {
        this.ip = ip;
    }

    public String getIpInstanceId() {
        return ipInstanceId;
    }

    public void setIpInstanceId(String ipInstanceId) {
        this.ipInstanceId = ipInstanceId;
    }

    public enum State {
        ASSOCIATING_IP_ADDRESS, CREATING_FIREWALL_RULE, READY
    }

}