package server;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.util.Vector;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import resource.Configurations;
import resource.Strings;
import helpers.BufferPrinter;
import helpers.Keyboard;

/**
 * The Console class will allow someone (presumably an admin) to manage the server
 * from a local machine. Currently its only functionality is to close the server,
 * but this can be expanded later.
 */
class Console implements Runnable {

	private TFTPServer mMonitorServer;
	public Console(TFTPServer monitorServer) {
		this.mMonitorServer = monitorServer;
	}
	public void run() {
		
		//(new BufferedReader(new InputStreamReader(System.in))).readLine();
		String quitCommand = Keyboard.getString();
		while(!quitCommand.equalsIgnoreCase("q")) {
			quitCommand = Keyboard.getString();
		}
		System.out.println(Strings.EXITING);
		TFTPServer.active.set(false);
		this.mMonitorServer.interruptSocketAndShutdown();
	}
}


public class TFTPServer implements Callback {
	
	/**
	 * Main function that starts the server.
	 */
	public static void main(String[] args) {
		TFTPServer listener = new TFTPServer();
		listener.start();
	}
	
	// Some class attributes.
	static AtomicBoolean active = new AtomicBoolean(true);
	Vector<Thread> threads;
	
	DatagramSocket serverSock = null;
	
//	final Lock lock = new ReentrantLock();
//	final Condition notEmpty = lock.newCondition();

	
	/**
	 * Constructor for TFTPServer that initializes the thread container 'threads'.
	 */
	public TFTPServer() {
		threads = new Vector<Thread>();
	}
	
	
	/**
	 * Handles operation of the server.
	 */
	public void start() {
		
		// Create the socket.
		
		DatagramPacket receivePacket = null;
		try {
			serverSock = new DatagramSocket(Configurations.SERVER_LISTEN_PORT);
			System.out.println("Server initiated on port " + Configurations.SERVER_LISTEN_PORT);
			serverSock.setSoTimeout(30000);
		} catch (SocketException e) {
			e.printStackTrace();
			System.exit(1);
		}
		
		// Create and start a thread for the command console.
		Thread console = new Thread(new Console(this), "command console");
		console.start();
		
		/*
		 * - Receive packets until the admin console gives the shutdown signal.
		 * - Since receiving a packet is a blocking operation, timeouts have been set to loop back
		 *   and check if the close signal has been given.
		 */
		while (active.get()) {
			try {
				// Create the packet for receiving.
				byte[] buffer = new byte[Configurations.MAX_MESSAGE_SIZE]; // Temporary. Will be replaced with exact value soon.
				receivePacket = new DatagramPacket(buffer, buffer.length);
				serverSock.receive(receivePacket);
			} catch (SocketTimeoutException e) {
				continue;
			} catch (SocketException e) {
				continue;
			} catch (IOException e) {
				System.out.println(Strings.SERVER_RECEIVE_ERROR);
				e.printStackTrace();
			}
			System.out.println(BufferPrinter.acceptConnectionMessage(Strings.SERVER_ACCEPT_CONNECTION, 
					receivePacket.getSocketAddress().toString()));
			Thread service = new Thread(new TFTPService(receivePacket, this), "Service");
			threads.addElement(service);
			service.start();
		}
		
		this.serverSock.close();
		
		/*
		 * Wait for all service threads to close before completely exiting.
		 */
		for(Thread t : threads) {
			try {
				t.join();
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void interruptSocketAndShutdown() {
		this.serverSock.close();
	}
	
	public synchronized void callback(long id) {
		for (Thread t : threads)
			if (t.getId() == id) {
				threads.remove(t);
				//notEmpty.signal();
				break;
			}
	}
}
