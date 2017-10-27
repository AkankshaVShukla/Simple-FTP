import static java.net.InetAddress.getByName;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.UnknownHostException;

public class Client {
	private String server;
	private int port;
	private String filename;
	private int windowsSize;
	private int mss;
	private volatile int numberOfSentPackets = 0;
	private volatile Segment[] buffer;
	private DatagramSocket clientSocket;
	private volatile int dataAck;
	volatile long totalPackets;
	int sizeOfLastPacket;

	final int RTTTimer = 2000; // milliseconds

	public Client(String server, int port, String filename, int windowsSize, int mss) {
		this.server = server;
		this.port = port;
		this.filename = filename;
		this.windowsSize = windowsSize;
		this.mss = mss;
		this.totalPackets = -1;
		this.sizeOfLastPacket = -1;

		// Go-back-N: need buffer
		buffer = new Segment[windowsSize];

		dataAck = -1;

		try {
			clientSocket = new DatagramSocket(0);
		} catch (SocketException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		try {
			clientSocket.connect(getByName(server), port);
		} catch (UnknownHostException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public static void main(String[] args) {

		if (args == null || args.length != 5) {
			System.out.println("Invalid input");
			return;
		}

		String server_host = args[0];
		int port = Integer.parseInt(args[1]); // 7735;
		String fileName = args[2];
		int windowSize = Integer.parseInt(args[3]);// 64;
		int mss = Integer.parseInt(args[4]);// 200; // bytes

		//for (windowSize = 2; windowSize <= 1000; windowSize *= 2) {
			//for (int i = 0; i < 2; ++i) {
				// System.out.println("MSS = " + windowSize);
				long startTime = System.currentTimeMillis();
				new Client(server_host, port, fileName, windowSize, mss).run();
				File file = new File(fileName);
				long fileSize = file.length();
				long endTime = System.currentTimeMillis();


				System.out.println("Window Size = " + windowSize + "\tDelay = " +
						(endTime - startTime) + "\tBytes Transferred = " + fileSize);
				//System.out
				//.println("Probability = 0.01\tDelay = " + (endTime - startTime) + "\tBytes Transferred = " + fileSize);
			//}
		//}

		// new Client(server_host, port, fileName, windowSize, mss).run();
	}

	public void run() {
		File file = new File(filename);
		byte data[] = new byte[mss];
		try {
			if (file.exists()) {
				long fileSize = file.length();

				totalPackets = (long) Math.ceil((double) fileSize / this.mss);
				sizeOfLastPacket = (int) fileSize % this.mss;

				/*
				 * System.out.println("Total packets = " + totalPackets +
				 * ", size of last packet = " + sizeOfLastPacket);
				 */

				FileInputStream fis = new FileInputStream(file);

				// Starting the acknowledgment listener
				AcknowledgmentServer ackServer = new AcknowledgmentServer(this.clientSocket);
				ackServer.start();
				boolean dataNotSent=false;
				
				while (numberOfSentPackets < totalPackets) {
					int index = numberOfSentPackets % windowsSize;
					
					/*if (buffer[index] == null
							|| (buffer[index] != null && buffer[index].packet.sequenceNumber <= dataAck)) {
						// send the packet
						if (fis.read(data) > -1)
							rdtSend(data, index);
					}*/
					
					Segment obj = operateBuffer(index, null);

					if (obj == null || (obj != null && obj.ackReceived == true && obj.packet.sequenceNumber != -1)) {
						// send the packet
						if (fis.read(data) > -1)
							if(!rdtSend(data, index)){
								dataNotSent=true;
							}
					}
						while(dataNotSent){
							if(rdtSend(data, index)){
								dataNotSent=false;
							}
						}
					
					/*
					 * if ((numberOfSentPackets <= dataAck) || (dataAck == -1 &&
					 * numberOfSentPackets < windowsSize)) { if(fis.read(data) >
					 * -1) rdtSend(data, index); }
					 */
				}

				// Send another packet with FTPPacket as null
				sendTermintingPacket();
				fis.close();
			}
		} catch (IOException e) {
			System.out.println(e);
		}
	}

	private void sendTermintingPacket() throws IOException {
		while (dataAck != totalPackets - 1) {
			// System.out.println("[TERMINATION]: dataAck = " + dataAck +
			// ", numberOfSentPackets" + numberOfSentPackets);
		}

		// System.out.println("Sending terminating packet");
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutputStream outStream = new ObjectOutputStream(bos);
		outStream.writeObject(null);
		byte[] sendData = bos.toByteArray();
		DatagramPacket dataPacket = new DatagramPacket(sendData, sendData.length, getByName(server), port);
		clientSocket.send(dataPacket);
	}

	public synchronized Segment operateBuffer(int index, Segment object){

		/*if(index >= buffer.length)
			return new Segment(new FTPPacket(-1, (short)0, (short)0));
		 */
		if(object != null && object.ackReceived == false && (buffer[index] == null || buffer[index].ackReceived)){
			buffer[index] = object;	
			return buffer[index];
		}
		else if(object != null && object.ackReceived == false && buffer[index] != null && buffer[index].packet.sequenceNumber == object.packet.sequenceNumber) {
			buffer[index] = object;	
			return buffer[index];
		}
		else if(object == null) {
			return buffer[index];
		}
		return null;

	}


	private boolean rdtSend(byte[] data, int index) throws IOException {
		// TODO Auto-generated method stub
		boolean isLast = false;
		FTPPacket packet = null;

		if (numberOfSentPackets == totalPackets - 1) {
			// This is the last packet
			byte[] lastPacket = new byte[sizeOfLastPacket];

			for (int i = 0; i < lastPacket.length; ++i) {
				lastPacket[i] = data[i];
			}

			packet = new FTPPacket(numberOfSentPackets, (short) 0, (short) 21845, lastPacket);

			isLast = true;
		}

		// Packet to be sent
		if (!isLast) {
			packet = new FTPPacket(numberOfSentPackets, (short) 0, (short) 21845, data);
		}

		short checksum = generateChecksum(serialize(packet));

		// System.out.println("Checksum = " + checksum);

		packet.checksum = checksum;

		// Go-Back protocol: store packet in buffer
		Segment newSeg = new Segment(packet);


		//buffer[numberOfSentPackets % windowsSize] = new Segment(packet);

		Segment seg = operateBuffer(index, newSeg);
		if(seg == null) {
			//Roll back
			return false;
		}

		// Send Packet to server
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		ObjectOutputStream outStream = new ObjectOutputStream(bos);
		outStream.writeObject(packet);
		byte[] sendData = bos.toByteArray();
		DatagramPacket dataPacket = new DatagramPacket(sendData, sendData.length, getByName(server), port);
		clientSocket.send(dataPacket);

		// System.out.println("Kuch bhi = " +
		// generateChecksum(serialize(packet)));


		newSeg.setSentTime();

		// Add the packet to the buffer
		numberOfSentPackets++;

		new RetransmitHandler(index, newSeg.packet.sequenceNumber).start();
		return true;
		// buffer[index].timer.schedule(new RetransmitHandler(index,
		// this.clientSocket), RTTTimer);
	}

	public static byte[] serialize(Object obj) throws IOException {
		try (ByteArrayOutputStream b = new ByteArrayOutputStream()) {
			try (ObjectOutputStream o = new ObjectOutputStream(b)) {
				o.writeObject(obj);
			}
			return b.toByteArray();
		}
	}

	public short generateChecksum(byte[] packet) {
		int checksum = 0;
		for (int i = 0; i < packet.length; i += 2) {
			int leftByte = (packet[i] << 8) & 0xFF00;
			int rightByte = (i + 1) < packet.length ? (packet[i + 1] & 0x00FF) : 0;
			checksum += (leftByte + rightByte);
			String hex = Integer.toHexString(checksum);
			if (hex.length() > 4) {
				int carry = Integer.parseInt(String.valueOf(hex.charAt(0)), 16);
				checksum = (Integer.parseInt(hex.substring(1, 5), 16) + carry);
			}
		}
		// Complement the checksum value
		return (short) (Integer.parseInt("FFFF", 16) - checksum);
	}

	private class AcknowledgmentServer extends Thread {
		private DatagramSocket socket;

		public AcknowledgmentServer(DatagramSocket socket) {
			this.socket = socket;
		}

		public void run() {
			byte databuffer[] = new byte[1024];
			int length = databuffer.length;
			DatagramPacket datagrampacket = new DatagramPacket(databuffer, length);
			if (!socket.isClosed()) {
				try {
					boolean send = true;
					while (send) {
						socket.receive(datagrampacket);
						ObjectInputStream outputStream = new ObjectInputStream(
								new ByteArrayInputStream(datagrampacket.getData()));
						FTPPacket packet = (FTPPacket) outputStream.readObject();
						synchronized(packet){
							if (packet.ackFlag == (short) 43690) {
								//dataAck = packet.sequenceNumber;

								if(dataAck < packet.sequenceNumber){
									dataAck = packet.sequenceNumber;
								}

								Segment obj = operateBuffer(packet.sequenceNumber % windowsSize, null);

								if(obj.packet.sequenceNumber == packet.sequenceNumber){
									obj.ackReceived = true;
									//operateBuffer(packet.sequenceNumber % windowsSize, obj);
								}
							}
						}
						if (dataAck == (totalPackets - 1)) {
							send = false;
						}
					}
				} catch (Exception e) {
					System.out.println("Error occured..." + e.getMessage());
				}
			}
		}
	}

	private class RetransmitHandler extends Thread {
		int packetIndex;
		int sequenceNumber;

		public RetransmitHandler(int packetIndex, int sequenceNumber) {
			this.sequenceNumber = sequenceNumber;
			this.packetIndex = packetIndex;
		}

		public void run() {

			boolean cancelTimer = false;
			synchronized(this){
				while (!cancelTimer) {

					Segment obj = operateBuffer(packetIndex, null);

					long sentTimestamp = obj.sentTimestamp;

					while ((System.currentTimeMillis() - sentTimestamp) < RTTTimer) {

					}

					obj = operateBuffer(packetIndex, null);

					if(obj.packet.sequenceNumber != this.sequenceNumber){
						//					System.out.println("[RET]: Dropping packet");
						break;
					}

					if (obj.packet != null && obj.ackReceived == true) {
						/*
						 * System.out.println("Timer cancelled for seq. no. " +
						 * buffer[packetIndex].packet.sequenceNumber);
						 */
						cancelTimer = true;
					} else {
						/*
						 * System.out.println("Timeout, sequence number = " +
						 * buffer[packetIndex].packet.sequenceNumber +
						 * ", dataAck = " + dataAck);
						 */
						// retransmit the packet
						FTPPacket packet = new FTPPacket(obj.packet.sequenceNumber, (short) 0,
								(short) 21845, obj.packet.data);

						short checksum = 0;
						try {
							checksum = generateChecksum(serialize(packet));
						} catch (IOException e3) {
							// TODO Auto-generated catch block
							e3.printStackTrace();
						}

						packet.checksum = checksum;


						Segment retransmittedSeg = new Segment(packet);

						//buffer[packetIndex] = new Segment(packet);

						// Send Packet to server
						ByteArrayOutputStream bos = new ByteArrayOutputStream();
						ObjectOutputStream outStream = null;
						try {
							outStream = new ObjectOutputStream(bos);
						} catch (IOException e2) {
							// TODO Auto-generated catch block
							e2.printStackTrace();
						}
						try {
							outStream.writeObject(packet);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

						byte[] sendData = bos.toByteArray();
						DatagramPacket dataPacket = null;

						try {
							dataPacket = new DatagramPacket(sendData, sendData.length, getByName(server), port);
						} catch (UnknownHostException e1) {
							// TODO Auto-generated catch block
							e1.printStackTrace();
						}

						try {
							clientSocket.send(dataPacket);
						} catch (IOException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}

						// Initiate the timer again
						retransmittedSeg.setSentTime();
						//buffer[packetIndex].setSentTime();
						operateBuffer(packetIndex, retransmittedSeg);
					}

				}
			}
		}
	}
}