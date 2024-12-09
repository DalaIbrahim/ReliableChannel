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

    // send packets to server
    public void sendPacketToServer(byte[] buffer, int seqNum) throws Exception {
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length, serverIP, serverPort);
        while (true) {
            // Send the packet
            socket.send(packet);
            System.out.println("[DATA TRANSMISSION]: " + seqNum + " | " + buffer.length);
    
            try {
                // Wait for ACK
                byte[] ackBuffer = new byte[1024];
                DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
                socket.receive(ackPacket);
    
                String ack = new String(ackPacket.getData(), 0, ackPacket.getLength());
                if (!ack.startsWith("ACK")) {
                    continue; // Ignore invalid ACKs
                }
    
                int ackSeqNum = Integer.parseInt(ack.split(" ")[1]);
                System.out.println("ACK received for sequence number " + ackSeqNum);
    
                if (ackSeqNum == -1) { // END acknowledgment
                    System.out.println("Transfer completed. Exiting...");
                    return; // Exit transmission loop
                }
    
                if (ackSeqNum >= base && ackSeqNum < base + WINDOW_SIZE) {
                    if (ackSeqNum == lastAckReceived) {
                        duplicateAckCount++;
                        if (duplicateAckCount == 3) {
                            // Resend the first packet in the window
                            int firstPacketSeqNum = base;
                            byte[] firstPacketData = window.get(firstPacketSeqNum);
                            DatagramPacket firstPacket = new DatagramPacket(firstPacketData, firstPacketData.length, serverIP, serverPort);
                            socket.send(firstPacket);
                            System.out.println("Resending packet with sequence number " + firstPacketSeqNum);
                            duplicateAckCount = 0; // Reset duplicate ACK count
                        }
                    } else {
                        duplicateAckCount = 0; // Reset duplicate ACK count
                    }
                    lastAckReceived = ackSeqNum;
                    base = ackSeqNum +1; // Move the window forward
                    break; // Successful acknowledgment, exit loop
                } else {
                    System.out.println("ACK for sequence number " + ackSeqNum + " is outside the window, ignoring.");
                }
            } catch (SocketTimeoutException e) {
                System.out.println("Timeout waiting for ACK, retransmitting packet with sequence number " + seqNum);
            }
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
            // Step 1: Send the file name
            sendFileName(fileName);
    
            // Step 2: Send the file content
            sendFile(fileName);
    
            // Step 3: Send the END packet and wait for acknowledgment
            byte[] endData = "END".getBytes();
            DatagramPacket endPacket = new DatagramPacket(endData, endData.length, serverIP, serverPort);
    
            while (true) {
                // Send the END packet
                socket.send(endPacket);
                System.out.println("[DATA TRANSMISSION]: END");
    
                try {
                    // Wait for acknowledgment
                    byte[] ackBuffer = new byte[1024];
                    DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length);
                    socket.receive(ackPacket);
    
                    String ack = new String(ackPacket.getData(), 0, ackPacket.getLength());
                    if (ack.startsWith("ACK")) {
                        int ackSeqNum = Integer.parseInt(ack.split(" ")[1]);
                        if (ackSeqNum == -1) { // Acknowledgment for END
                            System.out.println("ACK received for END packet. Transfer complete.");
                            return; // Exit the method
                        }
                    }
                } catch (SocketTimeoutException e) {
                    System.out.println("Timeout waiting for ACK of END packet, retransmitting...");
                }
            }
        } catch (Exception e) {
            System.err.println("Error during file transfer: " + e.getMessage());
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
