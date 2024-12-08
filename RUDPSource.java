import java.io.File;
import java.io.FileInputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

public class RUDPSource {
    private InetAddress serverIP; // The IP address of the server
    private int serverPort; // The port number of the server
    private DatagramSocket socket; // The socket used for communication
    private static final int TIMEOUT = 3000; // Timeout interval in milliseconds
    private static final int WINDOW_SIZE = 5; // Window size for sliding window protocol
     private Map<Integer, byte[]> window = new HashMap<>(); // Sliding window of packets
    private int base = 0; // Base of the window
    private int nextSeqNum = 0; // Next sequence number to be used
    private int duplicateAckCount = 0; // Count of duplicate ACKs
    private int lastAckReceived = -1; // Last ACK received
    private AtomicInteger sequenceNumber = new AtomicInteger(0); // Sequence number generator

    public RUDPSource(String serverIP, int serverPort) throws Exception {
        try {
        this.serverIP = InetAddress.getByName(serverIP);
        this.serverPort = serverPort;
        this.socket = new DatagramSocket();
        this.socket.setSoTimeout(TIMEOUT); // Set the timeout interval
        } catch (Exception e) {
            System.err.println("Error setting up client: " + e.getMessage());
        }
    }

    // Send packets to server
    public void sendPacketToServer(byte[] buffer, int seqNum) throws Exception {
        try {
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, serverIP, serverPort);
            while (true) {
                socket.send(packet);
                System.out.println("[DATA TRANSMISSION]: " + seqNum + " | " + buffer.length);
                try {
                    // Wait for ACK
                    byte[] ackBuffer = new byte[1024];
                    DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
                    socket.receive(ackPacket);
                    String ack = new String(ackPacket.getData(), 0, ackPacket.getLength());
                    int ackSeqNum = Integer.parseInt(ack.split(" ")[1]);
                    if (ack.startsWith("ACK")) {
                        System.out.println("ACK received for packet with sequence number " + ackSeqNum);
                        if (ackSeqNum < base || ackSeqNum >= base + WINDOW_SIZE) {
                            System.out.println("ACK for sequence number " + ackSeqNum + " is outside the window, ignoring.");
                            continue; // Ignore ACKs outside the window
                        }
                        if (ackSeqNum >= base) {
                            base = ackSeqNum + 1;
                            duplicateAckCount = 0;
                            break; // Exit the loop if ACK is received for a packet within the window
                        } else if (ackSeqNum == lastAckReceived) {
                            duplicateAckCount++;
                            if (duplicateAckCount == 3) {
                                System.out.println("3 duplicate ACKs received, retransmitting packet with sequence number " + base);
                                socket.send(new DatagramPacket(window.get(base), window.get(base).length, serverIP, serverPort));
                                duplicateAckCount = 0;
                            }
                        } else {
                            lastAckReceived = ackSeqNum;
                            duplicateAckCount = 0;
                        }
                    }
                } catch (SocketTimeoutException e) {
                    System.out.println("Timeout waiting for ACK, retransmitting packet with sequence number " + seqNum);
                }
            }
        } catch (Exception e) {
            System.err.println("Error sending packet: " + e.getMessage());
        }
    }

    // Send file to server
    public void sendFile(String fileName) throws Exception {
        File file = new File(fileName);
        FileInputStream fis = new FileInputStream(file);
        byte[] buffer = new byte[1024];
        int bytesRead;
        while ((bytesRead = fis.read(buffer)) != -1) {
            while (nextSeqNum >= base + WINDOW_SIZE) {
                // Wait until there is space in the window
            }
            int seqNum = sequenceNumber.getAndIncrement();
            String formattedData = seqNum + " " + new String(buffer, 0, bytesRead);
            byte[] packetData = formattedData.getBytes();  // Correctly formatted packet
            window.put(seqNum, packetData);
            sendPacketToServer(packetData, seqNum);
        }
        fis.close();
    }

    // Send file name to server and wait for ACK
    public void sendFileName(String fileName) throws Exception {
        int seqNum = sequenceNumber.getAndIncrement();
        String formattedFileName = seqNum + " " + fileName; // Prepend sequence number
        byte[] fileNameData = formattedFileName.getBytes();
        window.put(seqNum, fileNameData);
        sendPacketToServer(fileNameData, seqNum);
    }
    

    public void start(String fileName) throws Exception {
        try {
            // Send the file name
            sendFileName(fileName);

            // send file
            sendFile(fileName);

            int seqNum = sequenceNumber.getAndIncrement();
            sendPacketToServer(("END").getBytes(), seqNum);
            // Send the last packet in order to terminate the server 
            // Wait for acknowledgment of the END packet
            while (true) {
                try {
                    byte[] ackBuffer = new byte[1024];
                    DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
                    socket.receive(ackPacket);
                    String ack = new String(ackPacket.getData(), 0, ackPacket.getLength());
                    int ackSeqNum = Integer.parseInt(ack.split(" ")[1]);

                    if (ackSeqNum == -1) { // Acknowledgment for END
                        System.out.println("ACK received for END packet");
                        break;
                    }
                } catch (SocketTimeoutException e) {
                    System.out.println("Timeout waiting for ACK of END packet, retransmitting...");
                    sendPacketToServer(("END").getBytes(), seqNum);
                }
            }
        } catch (InterruptedException e) {
            System.err.println("Error during packet sending loop: " + e.getMessage());
        }
    }

    public static void main(String[] args) throws Exception {
        String recvHost = null;
        int recvPort = 0;
        String fileName = null;

        for (int i = 0; i < args.length; i++) {
            switch (args[i]) {
                case "-r":
                    String[] hostPort = args[++i].split(":");
                    recvHost = hostPort[0];
                    recvPort = Integer.parseInt(hostPort[1]);
                    break;
                case "-f":
                    fileName = args[++i];
                    break;
                default:
                    System.err.println("Usage: java RUDPSource -r <recvHost>:<recvPort> -f <fileName>");
                    return;
            }
        }

        if (recvHost == null || recvPort == 0 || fileName == null) {
            System.err.println("Usage: java RUDPSource -r <recvHost>:<recvPort> -f <fileName>");
            return;
        }

        // Create the client with the server's IP address and port number
        RUDPSource client = new RUDPSource(recvHost, recvPort);

        // Start the client to send and receive packets
        client.start(fileName);
    }
}
