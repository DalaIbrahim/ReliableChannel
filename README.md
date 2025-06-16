## 🧠 Project Overview

RUDP is a custom reliable data transfer protocol built on top of UDP, inspired by TCP’s core mechanisms. The goal is to enable reliable file transfer even in the presence of packet loss, delays, and reordering—conditions common in real-world networks.

The system consists of two Java programs:
- `RUDPSource.java`: Acts as the sender/client
- `RUDPDestination.java`: Acts as the receiver/server

Despite UDP's inherent unreliability, RUDP achieves robustness using:
- Sliding window flow control
- Timeout-based retransmissions
- Duplicate ACK handling
- Sequence tracking and acknowledgment logic

---

## 🔍 Key Features

- **Sliding Window Protocol** for efficient multi-packet transmission
- **Timeout & Retransmission** logic for lost or delayed packets
- **Selective Repeat ARQ**: Only the missing packets are resent
- **Artificial Packet Loss/Delay Simulation** for realistic testing
- **End-of-Transfer Signaling** with repeated `END` packets
- **File Integrity Verification** at the destination

---

## 📁 File Structure

```
├── RUDPSource.java         # Sender program responsible for sending packets
├── RUDPDestination.java    # Receiver program that reconstructs the file
├── test_files/             # Directory containing sample files for testing
└── README.md               # Project documentation
```

---

## 🛠 Technologies Used

- **Language:** Java  
- **Socket API:** DatagramSocket, DatagramPacket  
- **Protocol Concepts:** Sliding Window, Timeout Handling, Selective Retransmission  
- **Testing Environment:** Linux (preferred), CLI-based execution

---

## 🚀 How to Run

### 🖥️ **Receiver** (`RUDPDestination.java`)

1. **Compile the receiver:**
   ```bash
   javac RUDPDestination.java
   ```

2. **Run the receiver:**
   ```bash
   java RUDPDestination
   ```

> The receiver will begin listening on a default port. Ensure this port matches what the sender uses when connecting.

---

### 📤 **Sender** (`RUDPSource.java`)

1. **Compile the sender:**
   ```bash
   javac RUDPSource.java
   ```

2. **Run the sender with the following format:**
   ```bash
   java RUDPSource -r <receiver_ip>:<receiver_port> -f <file_path>
   ```

   **Example:**
   ```bash
   java RUDPSource -r 127.0.0.1:8080 -f example.txt
   ```

>  Make sure the receiver is already running before you start the sender.  
>  If running on different machines, ensure they are on the same network or that necessary ports are open.
