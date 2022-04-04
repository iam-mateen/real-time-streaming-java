package ca.yorku.rtsp.client.net;

import ca.yorku.rtsp.client.exception.RTSPException;
import ca.yorku.rtsp.client.model.Frame;
import ca.yorku.rtsp.client.model.Session;

import java.io.*;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.util.Random;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;

/**
 * This class represents a connection with an RTSP server.
 */
public class RTSPConnection {

    private static final int BUFFER_LENGTH = 0x10000;

    private Session session;

    // TODO Add additional fields, if necessary
    Socket socket;
    BufferedWriter writer;
    BufferedReader reader;

    DatagramSocket rtpSocket;
    DatagramSocket rtcpSocket;

    Timer timer;

    int state;
    int rtspSeqNo;
    String sessionId;

    final static String CRLF = "\r\n";


    /**
     * Establishes a new connection with an RTSP server. No message is sent at this point, and no stream is set up.
     *
     * @param session The Session object to be used for connectivity with the UI.
     * @param server  The hostname or IP address of the server.
     * @param port    The TCP port number where the server is listening to.
     * @throws RTSPException If the connection couldn't be accepted, such as if the host name or port number are invalid
     *                       or there is no connectivity.
     */
    public RTSPConnection(Session session, String server, int port) throws RTSPException {
        // TODO
        this.session = session;
        try {

            InetAddress serverIPAddress = InetAddress.getByName(server);
            this.socket = new Socket(serverIPAddress, port);
            this.writer = new BufferedWriter(new OutputStreamWriter(socket.getOutputStream()));
            this.reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            this.state = 0;

            System.out.println("New RTSP state: INIT");

        } catch (IOException e) {
            throw new RTSPException(e.getMessage());
        }
    }

    /**
     * Sends a SETUP request to the server. This method is responsible for sending the SETUP request, receiving the
     * response and retrieving the session identification to be used in future messages. It is also responsible for
     * establishing an RTP datagram socket to be used for data transmission by the server. The datagram socket should be
     * created with a random UDP port number, and the port number used in that connection has to be sent to the RTSP
     * server for setup. This datagram socket should also be defined to timeout after 1 second if no packet is
     * received.
     *
     * @param videoName The name of the video to be setup.
     * @throws RTSPException If there was an error sending or receiving the RTSP data, or if the RTP socket could not be
     *                       created, or if the server did not return a successful response.
     */
    public synchronized void setup(String videoName) throws RTSPException {
        // TODO
        try {
            int port = new Random().nextInt(10000) + 6666;
            rtpSocket = new DatagramSocket(port);
            rtpSocket.setSoTimeout(1000);

            this.rtspSeqNo = 1;
            this.writer.write("SETUP" + " " + videoName + " " + "RTSP/1.0" + CRLF);
            this.writer.write("Cseq: " + this.rtspSeqNo + CRLF);
            this.writer.write("TRANSPORT: RTP/UDP; client_port= " + port + CRLF + CRLF);
            this.writer.flush();

            //TODO Receive Response
            RTSPResponse response = readRTSPResponse();
            if (response.getResponseCode() != 200) {
                System.out.println("Invalid Server Response");
                throw new RTSPException("Invalid Server Response: " + response.getResponseCode());
            } else {
                this.state = 1;
                System.out.println("New RTSP state: READY");
            }
        } catch (Exception e) {
            throw new RTSPException(e.getMessage());
        }


    }

    /**
     * Sends a PLAY request to the server. This method is responsible for sending the request, receiving the response
     * and, in case of a successful response, starting a separate thread responsible for receiving RTP packets with
     * frames.
     *
     * @throws RTSPException If there was an error sending or receiving the RTSP data, or if the server did not return a
     *                       successful response.
     */
    public synchronized void play() throws RTSPException {
        //if Ready
        if (this.state == 1) {
            try {

                this.rtspSeqNo++;
                this.writer.write("PLAY" + " " + this.session.getVideoName() + " RTSP/1.0" + CRLF);
                this.writer.write("CSeq: " + this.rtspSeqNo + CRLF);
                this.writer.write("Session: " + this.sessionId + CRLF + CRLF);
                this.writer.flush();

                RTSPResponse response = readRTSPResponse();
                if (response.getResponseCode() != 200) {
                    System.out.println("Invalid Server Response");
                    throw new RTSPException("Invalid Server Response: " + response.getResponseCode());
                } else {
                    this.state = 2;
                    System.out.println("New RTSP state: PLAYING");
                    new RTPReceivingThread().start();
                }

            } catch (Exception e) {
                throw new RTSPException(e.getMessage());
            }
        }
    }




    private class RTPReceivingThread extends Thread {
        /**
         * Continuously receives RTP packets until the thread is cancelled. Each packet received from the datagram
         * socket is assumed to be no larger than BUFFER_LENGTH bytes. This data is then parsed into a Frame object
         * (using the parseRTPPacket method) and the method session.processReceivedFrame is called with the resulting
         * packet. The receiving process should be configured to timeout if no RTP packet is received after two seconds.
         */
        @Override
        public void run() {
            // TODO
            try {

                byte[] buffer = new byte[BUFFER_LENGTH];
                DatagramPacket packet = new DatagramPacket(buffer, BUFFER_LENGTH);

                timer = new Timer();
                timer.schedule(new TimerTask() {
                    @Override
                    public void run() {
                        try {

                            rtpSocket.receive(packet);
                            Frame frame = parseRTPPacket(packet);
                            session.processReceivedFrame(frame);

                        } catch (Exception e) {}
                    }
                }, 0, 20);





            } catch (Exception e) {
                e.printStackTrace();
            }
        }

    }

    /**
     * Sends a PAUSE request to the server. This method is responsible for sending the request, receiving the response
     * and, in case of a successful response, stopping the thread responsible for receiving RTP packets with frames.
     *
     * @throws RTSPException If there was an error sending or receiving the RTSP data, or if the server did not return a
     *                       successful response.
     */
    public synchronized void pause() throws RTSPException {
        // TODO
        if (this.state == 2) {

            try {
                this.rtspSeqNo++;
                writer.write("PAUSE" + " " + this.session.getVideoName() + " RTSP/1.0" + CRLF);
                writer.write("CSeq: " + this.rtspSeqNo + CRLF);
                writer.write("Session: " + this.sessionId + CRLF + CRLF);
                writer.flush();


                RTSPResponse response = readRTSPResponse();

                if (response.getResponseCode() != 200) {
                    System.out.println("Invalid Server Response");
                    throw new RTSPException("Invalid Server Response: " + response.getResponseCode());
                } else {
                    this.state = 1;
                    System.out.println("New RTSP state: READY");
                    //TODO STOP PAUSE THREAD
                }
            } catch (Exception e) {
                throw new RTSPException(e.getMessage());
            }
        }
    }

    /**
     * Sends a TEARDOWN request to the server. This method is responsible for sending the request, receiving the
     * response and, in case of a successful response, closing the RTP socket. This method does not close the RTSP
     * connection, and a further SETUP in the same connection should be accepted. Also this method can be called both
     * for a paused and for a playing stream, so the thread responsible for receiving RTP packets will also be
     * cancelled.
     *
     * @throws RTSPException If there was an error sending or receiving the RTSP data, or if the server did not return a
     *                       successful response.
     */
    public synchronized void teardown() throws RTSPException {
        // TODO

        try {
            this.rtspSeqNo++;
            writer.write("TEARDOWN" + " " + this.session.getVideoName() + " RTSP/1.0" + CRLF);
            writer.write("CSeq: " + this.rtspSeqNo + CRLF);
            writer.write("Session: " + this.sessionId + CRLF + CRLF);
            writer.flush();

            RTSPResponse response = readRTSPResponse();

            if (response.getResponseCode() != 200) {
                System.out.println("Invalid Server Response");
                throw new RTSPException("Invalid Server Response: " + response.getResponseCode());
            } else {
                this.state = 0;
                System.out.println("New RTSP state: INIT");
                rtpSocket.close();
                timer.cancel();

            }

        } catch (Exception e) {
            throw new RTSPException(e.getMessage());
        }
    }

    /**
     * Closes the connection with the RTSP server. This method should also close any open resource associated to this
     * connection, such as the RTP connection, if it is still open.
     */
    public synchronized void closeConnection() {
        // TODO
        try {

            socket.close();
            rtpSocket.close();
            rtcpSocket.close();

        } catch (Exception e) {
        }
    }

    /**
     * Parses an RTP packet into a Frame object.
     *
     * @param packet the byte representation of a frame, corresponding to the RTP packet.
     * @return A Frame object.
     */
    public static Frame parseRTPPacket(DatagramPacket packet) {
        // TODO

        byte payloadType = 0;
        boolean marker = false;
        short sequenceNumber = -1;
        int timestamp = -1;
        byte[] payload = new byte[packet.getLength()];


        if (packet.getLength() >= 12) {
            byte[] header = new byte[12];
            for (int i = 0; i < 12; i++)
                header[i] = packet.getData()[i];
            int payloadSize = packet.getLength() - 12;
            payload = new byte[payloadSize];
            for (int i = 12; i < packet.getLength(); i++)
                payload[i - 12] = packet.getData()[i];

            //interpret the changing fields of the header:
            int version = (header[0] & 0xFF) >>> 6;
            payloadType = (byte) (header[1] & 0x7F);
            sequenceNumber = (short) ((header[3] & 0xFF) + ((header[2] & 0xFF) << 8));
            timestamp = (header[7] & 0xFF) + ((header[6] & 0xFF) << 8) + ((header[5] & 0xFF) << 16) + ((header[4] & 0xFF) << 24);
        }

        Frame frame = new Frame(payloadType, marker, sequenceNumber, timestamp, payload);

        return frame; // Replace with a proper Frame
    }

    /**
     * Reads and parses an RTSP response from the socket's input.
     *
     * @return An RTSPResponse object if the response was read completely, or null if the end of the stream was reached.
     * @throws IOException   In case of an I/O error, such as loss of connectivity.
     * @throws RTSPException If the response doesn't match the expected format.
     */
    public RTSPResponse readRTSPResponse() throws IOException, RTSPException {
        int responseCode = -1;
        String rtspVersion = "";
        String responseMessage = "";

        String statusLine = "";

        try {

            statusLine = this.reader.readLine();
            System.out.println("1st: " + statusLine);
            responseCode = parseStatusResponse(statusLine);

            if (responseCode == 200) {
                //CSeq
                statusLine = this.reader.readLine();
                System.out.println("2nd: " + statusLine);

                //Session ID
                statusLine = this.reader.readLine();
                this.sessionId = setRTSPid(statusLine);
                System.out.println("3rd: " + statusLine);

                statusLine = this.reader.readLine();
                System.out.println("4th: " + statusLine);
            }
            return new RTSPResponse(rtspVersion, responseCode, responseMessage);
        } catch (IOException ex) {
            throw new IOException(ex.getMessage());
        } catch (Exception e) {
            throw new RTSPException(e.getMessage());
        }

    }

    private int parseStatusResponse(String s) throws RTSPException {
        try {

            int status;
            StringTokenizer tokens = new StringTokenizer(s);
            tokens.nextToken();
            status = Integer.valueOf(tokens.nextToken());
            return status;

        } catch (Exception e) {
            throw new RTSPException(e.getMessage());
        }
    }

    private String setRTSPid(String s) throws RTSPException {
        try {

            StringTokenizer tokens = new StringTokenizer(s);
            tokens.nextToken();
            String id = tokens.nextToken();
            return id;

        } catch (Exception e) {
            throw new RTSPException(e.getMessage());
        }
    }

}
