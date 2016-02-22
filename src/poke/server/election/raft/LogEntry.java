package poke.server.election.raft;

public class LogEntry {
	private int term;
//	private int index;
//	private String command;
	private poke.comm.App.Request request;
	
	public poke.comm.App.Request getRequest() {
		return request;
	}

	public void setRequest(poke.comm.App.Request request) {
		this.request = request;
	}

	public LogEntry(int term) {
		this.term = term;
//		this.index = -1;
//		this.command = null;
		this.request = null;
	}
	
	public LogEntry(int term, poke.comm.App.Request req) {
		this.term = term;
//		this.index = index;
//		this.command = command;
		this.request = req;
	}

	public int getTerm() {
		return term;
	}

	/*public int getIndex() {
		return index;
	}

	public String getCommand() {
		return command;
	}
*/
	public void setTerm(int term) {
		this.term = term;
	}

/*	public void setIndex(int index) {
		this.index = index;
	}

	public void setCommand(String command) {
		this.command = command;
	}
*/	
	public String toString() {
		return "logTerm:"+term;
	}
	
	
}
