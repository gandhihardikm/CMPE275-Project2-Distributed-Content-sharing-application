package poke.server.election.raft;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

import poke.server.election.RaftElectionManager;
import poke.server.managers.ElectionManager;

public class RaftNode {
	public static enum NodeState{ LEADER, FOLLOWER, CANDIDATE }
	private static AtomicReference<RaftNode> instance = new AtomicReference<RaftNode>();

	private int nodeId;
	private NodeState nodeState;
	private int term;
	private int votedFor;
	private int commitIndex;
	private int lastLogIndex;
	private int lastLogTerm;
	private int timeout; // TODO - Don't know why I have given this!!!
	private ConcurrentHashMap<Integer, RaftPeer> peerTable;
	private List<LogEntry> logList;
	private List<Integer> replicationList;

	public List<Integer> getReplicationList() {
		return replicationList;
	}


	public void setReplicationList(List<Integer> replicationList) {
		this.replicationList = replicationList;
	}


	private RaftNode(int nodeId, NodeState nodeState) {
		this.nodeId = nodeId;
		this.nodeState = nodeState;
		this.term = 0;
		this.votedFor = -1;
		this.logList =  new ArrayList<LogEntry>(50);
		this.replicationList = new ArrayList<Integer>(50);
		this.commitIndex = 0;
		this.logList.add(0, new LogEntry(0));
		this.replicationList.add(0,2);
		// for testing purpose... TODO - this should be done while adding new log in list 
//		if (nodeId == 0) {
//			this.lastLogIndex = 2;
//			this.lastLogTerm = 0;
//			this.logList.add(1, new LogEntry(0));
//			this.logList.add(2, new LogEntry(0));
//			this.replicationList.add(1, ElectionManager.getConf().getRaftMajority()-1);
//			this.replicationList.add(2, ElectionManager.getConf().getRaftMajority()-1);
//		} else if (nodeId == 1) {
//			this.lastLogIndex = 1;
//			this.lastLogTerm = 0;
//			this.logList.add(1, new LogEntry(1));
//			// TODO - add to replicatioList when inserting log into logList
//			this.replicationList.add(1, ElectionManager.getConf().getRaftMajority()-1);
//		}
//		else {
		this.lastLogIndex = 0;
		this.lastLogTerm = 0;
//		}
		this.timeout = 0;
		peerTable = new ConcurrentHashMap<Integer, RaftPeer>();
	}


	public static RaftNode getInstance(int nodeId, NodeState nodeState) {
		instance.compareAndSet(null, new RaftNode(nodeId, nodeState));
		return instance.get();
	}

	public synchronized int getLastLogIndex() {
		return lastLogIndex;
	}

	public synchronized void setLastLogIndex(int lastLogIndex) {
		this.lastLogIndex = lastLogIndex;
	}

	public synchronized int getLastLogTerm() {
		return lastLogTerm;
	}

	public synchronized void setLastLogTerm(int lastLogTerm) {
		this.lastLogTerm = lastLogTerm;
	}

	public NodeState getNodeState() {
		return nodeState;
	}

	public int getTerm() {
		return term;
	}

	public int getVotedFor() {
		return votedFor;
	}

	public int getCommitIndex() {
		return commitIndex;
	}

	public int getTimeout() {
		return timeout;
	}

	public ConcurrentHashMap<Integer, RaftPeer> getPeerTable() {
		return peerTable;
	}

	public List<LogEntry> getLogList() {
		return logList;
	}

	public void setNodeState(NodeState nodeState) {
		if (nodeState == NodeState.LEADER)
			ElectionManager.getInstance().setLeaderNode(nodeId);
		this.nodeState = nodeState;
	}

	public void setTerm(int term) {
		this.term = term;
	}

	public void setVotedFor(int votedFor) {
		this.votedFor = votedFor;
	}

	public synchronized void setCommitIndex(int commitIndex) {
		this.commitIndex = commitIndex;
	}

	public synchronized void incrementCommitIndex() {
		this.commitIndex++;
	}

	public void setTimeout(int timeout) {
		this.timeout = timeout;
	}

	public void setPeerTable(ConcurrentHashMap<Integer, RaftPeer> peerTable) {
		this.peerTable = peerTable;
	}

	public void setLogList(List<LogEntry> logList) {
		this.logList = logList;
	}

	public synchronized void addRaftPeer(RaftPeer raftPeer) {
		if (!this.peerTable.containsKey(raftPeer.getPeerId())) {
			this.peerTable.put(raftPeer.getPeerId(), raftPeer);
		} else {
			System.out.println("#### else...!!!");
		}

	}

	public RaftPeer getRaftPeer(int nodeId) {
		if (this.peerTable.containsKey(nodeId)) {
			return this.peerTable.get(nodeId);
		}
		return null;
	}

	public synchronized void addLogEntry(int index, LogEntry logEntry) {
		if (index < logList.size()) {
			logList.remove(index);
		}
		logList.add(index, logEntry);
		lastLogIndex = index;
		lastLogTerm = logEntry.getTerm();

	}

	public synchronized LogEntry getLogEntry(int index) {
		System.out.println("****** index::"+index+"...size::"+logList.size());
		if (index >= logList.size() || index < 0) {
			return null;
		}
		return  logList.get(index);
	}


	public int getNodeId() {
		return nodeId;
	}


	public void setNodeId(int nodeId) {
		this.nodeId = nodeId;
	}

	@Override
	public String toString() {
		return "Node ID:"+nodeId+" nodeState:"+nodeState+" term:"+term+" commitIndex:"+commitIndex+
				" lastLogIndex:"+lastLogIndex+" lastLogTerm:"+lastLogTerm+" logEntries: [ "+logDetails()+" ]"+
				" peerDetails: { "+peerDetails()+" }";
	}

	private String logDetails() {
		StringBuilder sb = new StringBuilder();
		for (int i=0; i< logList.size(); i++)
			sb.append(logList.get(i)+" | ");
		return sb.toString();
	}

	private String peerDetails() {
		StringBuilder sb = new StringBuilder();

		for(Map.Entry<Integer, RaftPeer> entry: peerTable.entrySet())
			sb.append(entry.getValue()+" | ");

		return sb.toString();
	}

	public synchronized void initializePeerNodes() {
		for (RaftPeer raftPeer : peerTable.values()) {
			raftPeer.setNextIndex(lastLogIndex+1);
		}
	}

	public synchronized void initializeReplicatinCount(int logIndex) {
		this.replicationList.add(logIndex, 1);
	}

	public synchronized void incrementReplicationCount(int logIndex) {
		int cnt = this.replicationList.get(logIndex);
		if (cnt < ElectionManager.getConf().getRaftMajority())
			this.replicationList.set(logIndex, cnt+1);
	}

	public synchronized boolean isReplicatedOnMajority(int logIndex) {
		int cnt = this.replicationList.get(logIndex);

		if (cnt < ElectionManager.getConf().getRaftMajority())
			return false;

		return true;
	}

}
