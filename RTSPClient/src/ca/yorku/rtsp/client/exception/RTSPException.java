
package ca.yorku.rtsp.client.exception;

public class RTSPException extends Exception {

    public RTSPException(String message) {
        super(message);
    }

    public RTSPException(Throwable cause) {
        super(cause);
    }

    public RTSPException(String message, Throwable cause) {
        super(message, cause);
    }
}
