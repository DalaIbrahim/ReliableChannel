import java.io.FileOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;

public class RUDPDestination {
    private int recvPort;
    private DatagramSocket socket;
    private Map<Integer, byte[]> receivedPackets = new HashMap<>();
    private int expectedSeqNum = 0;
    private String receivedFileName = "received_file-copy";
    private static final double LOSS_RATE = 0.2; // 20% packet loss
    private static final int MIN_DELAY = 100; // Minimum delay in milliseconds
    private static final int MAX_DELAY = 500; // Maximum delay in milliseconds
    private Random random = new Random();

    public RUDPDestination(int recvPort) throws Exception {
        this.recvPort = recvPort;
        this.socket = new DatagramSocket(recvPort);
    }

    public void receiveFile() throws Exception {
        byte[] buffer = new byte[1024];
        DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

        while (true) {
            socket.receive(packet);

            // Simulate packet loss
            if (random.nextDouble() < LOSS_RATE) {
                System.out.println("[PACKET LOST]: Simulated loss of packet from " + packet.getAddress() + ":" + packet.getPort());
                continue;
            }

            // Simulate delay
            int delay = MIN_DELAY + random.nextInt(MAX_DELAY - MIN_DELAY + 1);
            Thread.sleep(delay);
            System.out.println("[PACKET DELAYED]: Simulated delay of " + delay + "ms");

            String receivedData = new String(packet.getData(), 0, packet.getLength());

            if (receivedData.equals("END")) {
                System.out.println("[COMPLETE] File transfer complete.");
                InetAddress senderIP = packet.getAddress();
                int senderPort = packet.getPort();
            
                // Send ACK for END
                sendAck(senderIP, senderPort, -1); // Use -1 to represent END acknowledgment
                writeFile();
                break;
            }

            InetAddress senderIP = packet.getAddress();
            int senderPort = packet.getPort();

            if (expectedSeqNum == 0 && receivedFileName.equals("received_file-copy")) {
                receivedFileName = receivedData;
                System.out.println("[DATA RECEPTION]: 0 | " + receivedData.length() + " | OK");
                sendAck(senderIP, senderPort, 0);
                expectedSeqNum++;
                continue;
            }

            String[] parts = receivedData.split(" ", 2);
            int seqNum = Integer.parseInt(parts[0]);
            byte[] fileData = parts[1].getBytes();

            if (seqNum == expectedSeqNum) {
                receivedPackets.put(seqNum, fileData);
                System.out.println("[DATA RECEPTION]: " + seqNum + " | " + fileData.length + " | OK");
                sendAck(senderIP, senderPort, seqNum);
                expectedSeqNum++;
            } else {
                System.out.println("[DATA RECEPTION]: " + seqNum + " | " + fileData.length + " | DISCARDED");
                sendAck(senderIP, senderPort, expectedSeqNum - 1);
            }
        }
    }

    private void sendAck(InetAddress senderIP, int senderPort, int seqNum) throws Exception {
        String ackMessage = "ACK " + seqNum;
        byte[] ackBuffer = ackMessage.getBytes();
        DatagramPacket ackPacket = new DatagramPacket(ackBuffer, ackBuffer.length, senderIP, senderPort);
        socket.send(ackPacket);
        System.out.println("ACK sent for sequence number " + seqNum);
    }

    private void writeFile() throws Exception {
        FileOutputStream fos = new FileOutputStream(receivedFileName);
        for (int i = 0; i < expectedSeqNum; i++) {
            if (receivedPackets.containsKey(i)) {
                fos.write(receivedPackets.get(i));
            }
        }
        fos.close();
        System.out.println("File saved as " + receivedFileName);
    }

    public static void main(String[] args) throws Exception {
        if (args.length != 2 || !args[0].equals("-p")) {
            System.err.println("Usage: java RUDPDestination -p <recvPort>");
            return;
        }

        int recvPort = Integer.parseInt(args[1]);
        RUDPDestination server = new RUDPDestination(recvPort);
        server.receiveFile();
    }
}