
package ca.yorku.rtsp.client.model;

import ca.yorku.rtsp.client.exception.RTSPException;

public interface SessionListener {

    public void exceptionThrown(RTSPException exception);

    public void frameReceived(Frame frame);

    public void videoNameChanged(String videoName);
}