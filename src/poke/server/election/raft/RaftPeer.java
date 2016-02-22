package poke.server.election.raft;

public class RaftPeer {
	private int nodeId;
	private int nextIndex;
	private int matchIndex;
	private boolean voteGranted;
	
	public RaftPeer(int peerId, int nextIndex) {
		this.nodeId = peerId;
		this.nextIndex = nextIndex;
		this.matchIndex = 0;
		this.voteGranted = false;
	}
	
	public int getPeerId() {
		return nodeId;
	}
	public int getNextIndex() {
		return nextIndex;
	}
	public int getMatchIndex() {
		return matchIndex;
	}
	public boolean isVoteGranted() {
		return voteGranted;
	}
	public void setPeerId(int peerId) {
		this.nodeId = peerId;
	}
	public void setNextIndex(int nextIndex) {
		this.nextIndex = nextIndex;
	}
	public void setMatchIndex(int matchIndex) {
		this.matchIndex = matchIndex;
	}
	public void setVoteGranted(boolean voteGranted) {
		this.voteGranted = voteGranted;
	}
	
	public String toString() {
		return "peerId:"+nodeId+" nextIndex"+nextIndex+" matchIndex"+matchIndex+" voteGranted:"+voteGranted;
	}
	
}
