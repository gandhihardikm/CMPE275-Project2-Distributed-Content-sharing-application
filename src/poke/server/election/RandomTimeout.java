package poke.server.election;

public class RandomTimeout {
	private static final int MINTIMEOUT = 10000; // in milis
	private static final int MINELECTIONTIMEOUT = 10000; // in milis
	private static final java.util.Random random = new java.util.Random();
	
	public static int generateTimeout() {
//		Random random = new Random();
		int randomTimeout = random.nextInt(2000);
		randomTimeout += MINTIMEOUT;
		return randomTimeout;
	}
	
	public static int generateElectionTimeout() {
//		Random random = new Random();
		int randomTimeout = random.nextInt(5000);
		randomTimeout += MINELECTIONTIMEOUT;
		return randomTimeout;
	}

}
