/*
 * copyright 2014, gash
 * 
 * Gash licenses this file to you under the Apache License,
 * version 2.0 (the "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at:
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations
 * under the License.
 */
package poke.server.managers;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

import java.net.SocketAddress;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.comm.App.Request;
import poke.core.Mgmt.AppendEntries;
import poke.core.Mgmt.AppendEntriesResponse;
import poke.core.Mgmt.Entry;
import poke.core.Mgmt.Heartbeat;
import poke.core.Mgmt.Management;
import poke.core.Mgmt.MgmtHeader;
import poke.server.conf.ServerConf;
import poke.server.conf.ServerConf.AdjacentConf;
import poke.server.election.ElectionIDGenerator;
import poke.server.election.raft.LogEntry;
import poke.server.election.raft.RaftNode;
import poke.server.election.raft.RaftPeer;
import poke.server.management.ManagementQueue;
import poke.server.managers.HeartbeatData.BeatStatus;

import com.google.protobuf.GeneratedMessage;

/**
 * A server can contain multiple, separate interfaces that represent different
 * connections within an overlay network. Therefore, we will need to manage
 * these edges (e.g., heart beats) separately.
 * 
 * Essentially, there should be one HeartbeatManager per node (assuming a node
 * does not support more than one overlay network - which it could...).
 * Secondly, the HB manager holds the network connections of the graph that the
 * other managers rely upon.
 * 
 * TODO have a common area that the server uses to hold connections (one writer
 * - HB and many readers).
 * 
 * @author gash
 * 
 */
public class HeartbeatManager extends Thread {
	protected static Logger logger = LoggerFactory.getLogger("heartbeat");
	protected static AtomicReference<HeartbeatManager> instance = new AtomicReference<HeartbeatManager>();

	// frequency that heartbeats are checked
	static final int sHeartRate = 5000; // msec

	private static ServerConf conf;

	ManagementQueue mqueue;
	boolean forever = true;

	// the in/out queues for sending heartbeat messages
	// TODO this queue should be shared across managers
	ConcurrentHashMap<Channel, HeartbeatData> outgoingHB = new ConcurrentHashMap<Channel, HeartbeatData>();
	ConcurrentHashMap<Integer, HeartbeatData> incomingHB = new ConcurrentHashMap<Integer, HeartbeatData>();

	public static HeartbeatManager initManager(ServerConf conf) {
		System.out.println("\nTODO HB QUEUES SHOULD BE SHARED!\n");
		HeartbeatManager.conf = conf;
		instance.compareAndSet(null, new HeartbeatManager());
		return instance.get();
	}

	public static HeartbeatManager getInstance() {
		// TODO throw exception if not initialized!
		return instance.get();
	}

	/**
	 * initialize the heartbeatMgr for this server
	 * 
	 * @param nodeId
	 *            The server's (this) ID
	 */
	protected HeartbeatManager() {
	}

	public ConcurrentHashMap<Integer, HeartbeatData> getIncomingHB() {
		return incomingHB;
	}

	/**
	 * create/register expected connections that this node will make. These
	 * edges are connections this node is responsible for monitoring.
	 * 
	 * @deprecated not sure why this is needed
	 * @param edges
	 */
	public void initNetwork(AdjacentConf edges) {
	}

	/**
	 * update information on a node we monitor
	 * 
	 * @param nodeId
	 */
	public void processRequest(Management mgmt) {
//		Heartbeat req = mgmt.getBeat();
		AppendEntries ae = mgmt.getAppendEntries();

		if (ae == null)
			return;

		HeartbeatData hd = incomingHB.get(mgmt.getHeader().getOriginator());
		if (hd == null) {
			// TODO should we accept this node? Not likely without security
			// credentials as this would allow anyone to connect to our network.

			logger.error("Unknown heartbeat received from node ", mgmt.getHeader().getOriginator());
			return;
		} else {
			logger.info("HeartbeatManager.processRequest() HB received from " + mgmt.getHeader().getOriginator());
			//if (logger.isDebugEnabled())
			logger.info("***Following leader node "+mgmt.getHeader().getOriginator()+" for term "+ae.getTerm());

//			hd.setFailures(0);
//			hd.setLastBeat(System.currentTimeMillis());
			respondToAppendEntries(mgmt, hd);
		}
	}

	private void respondToAppendEntries(Management mgmt, HeartbeatData hd) {
		AppendEntries ae = mgmt.getAppendEntries();

		// TODO - check for safety conditions
//		boolean isSafe = ensureSafety(ae);
		RaftNode thisNode = ElectionManager.getInstance().getRaftNode();

		AppendEntriesResponse.Builder aer = AppendEntriesResponse.newBuilder();

		if (ae.getTerm() >= thisNode.getTerm()) {
			// update beat received statistics
			hd.setFailures(0);
			hd.setLastBeat(System.currentTimeMillis());
			hd.setIsLastBeat(true);

			// update term id
//			ElectionManager.getInstance().setLeaderNode(mgmt.getHeader().getOriginator());
			ElectionIDGenerator.setMasterID(ae.getTerm());

			// build appendEntriesResponse
			aer.setTerm(ae.getTerm());
			logger.info("***@@@"+ae.getPrevLogIndex());
			LogEntry logEntry = thisNode.getLogEntry(ae.getPrevLogIndex());
			if (logEntry != null && logEntry.getTerm() == ae.getPrevLogTerm()) {

				thisNode.setTerm(ae.getTerm());
				thisNode.setNodeState(RaftNode.NodeState.FOLLOWER);
				ElectionManager.getInstance().setLeaderNode(mgmt.getHeader().getOriginator());

				aer.setSuccess(true);

				if (ae.hasEntries()) {
					// update logList, lastLogIndex and lastLogTerm
					//thisNode.addLogEntry(ae.getPrevLogIndex()+1, new LogEntry(ae.getEntries().getTerm()));
					LogEntry newLogEntry = new LogEntry(ae.getEntries().getTerm(), ae.getEntries().getRequest());
					thisNode.addLogEntry(ae.getPrevLogIndex()+1, newLogEntry);
					thisNode.initializeReplicatinCount(ae.getPrevLogIndex()+1);
					thisNode.setLastLogIndex(ae.getPrevLogIndex()+1);
					thisNode.setLastLogTerm(ae.getEntries().getTerm());
					aer.setMatchIndex(ae.getPrevLogIndex()+1);
				} else {
					aer.setMatchIndex(ae.getPrevLogIndex());
				}

				if (thisNode.getCommitIndex() < ae.getCommitIndex()){
					thisNode.incrementCommitIndex();
					LogEntry le = thisNode.getLogEntry(thisNode.getCommitIndex());
					Request req = le.getRequest();
					ConnectionManager.broadcast(req);
					if(req.getHeader().getIsClient())
						ConnectionManager.broadcastToClusters(req);
				}
			} else {
				aer.setSuccess(false);
				aer.setMatchIndex(0);
			}
		}
		else {
			logger.info("***Appendentry not safe!!!");
			aer.setTerm(thisNode.getTerm());
			aer.setSuccess(false);
			aer.setMatchIndex(0);
		}

		MgmtHeader.Builder mhb = MgmtHeader.newBuilder();
		mhb.setOriginator(conf.getNodeId());
		mhb.setTime(System.currentTimeMillis());
		mhb.setSecurityCode(-999);

		Management.Builder mb = Management.newBuilder();
		mb.setHeader(mhb.build());
		mb.setAppendEntriesResponse(aer.build());

		// now send it to the requester
		try {
			ConnectionManager.getConnection(mgmt.getHeader().getOriginator(), true).writeAndFlush(mb.build());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
//		logger.info("@@@@ "+thisNode.toString());
	}

	public void processAppendEntriesResponse(Management mgmt) {
		AppendEntriesResponse aer = mgmt.getAppendEntriesResponse();
		RaftNode thisNode = ElectionManager.getInstance().getRaftNode();

		if(aer.getTerm() == thisNode.getTerm()) {
			// correct term process response
			// if success - update matchIndex and nextIndex of raftpeer in peerTable;
			//				Also, inc replication count in replicationList.
			//				If replication is successful on majority - set commitIndex = matchIndex
			// if failure - dec nextIndex; set matchIndex to -1 and let process repeat itself
			RaftPeer peerNode = thisNode.getPeerTable().get(mgmt.getHeader().getOriginator());
			peerNode.setMatchIndex(aer.getMatchIndex());
			if(aer.getSuccess()) {
				peerNode.setNextIndex(aer.getMatchIndex()+1);
				if (thisNode.getCommitIndex() < aer.getMatchIndex()) {
					thisNode.incrementReplicationCount(aer.getMatchIndex());

					if (thisNode.isReplicatedOnMajority(aer.getMatchIndex())){
//						thisNode.incrementCommitIndex();
						int commintIndex = thisNode.getCommitIndex();
						thisNode.setCommitIndex(aer.getMatchIndex());
						for (int i = commintIndex + 1; i <= aer.getMatchIndex(); i++ ){
							LogEntry le = thisNode.getLogEntry(i);
							Request req = le.getRequest();
							ConnectionManager.broadcast(req);
							logger.info(req.getHeader().toString());
							if(req.getHeader().getIsClient())
								ConnectionManager.broadcastToClusters(req);
						}
					}
				}
			} else {
				peerNode.setNextIndex(peerNode.getNextIndex()-1);
			}
		} else {
			// incorrect term of this node..update term and state to follower
			logger.info("AppendEntriResponse term is greater than node term...update thisNode to Term "+thisNode.getNodeId());
			thisNode.setTerm(aer.getTerm());
			thisNode.setNodeState(RaftNode.NodeState.FOLLOWER);
		}

		logger.info("@@@@ "+thisNode.toString());
	}

	/**
	 * This is called by the HeartbeatPusher when this node requests a
	 * connection to a node, this is called to register interest in creating a
	 * connection/edge.
	 * 
	 * @param node
	 */
	protected void addAdjacentNode(HeartbeatData node) {
		if (node == null || node.getHost() == null || node.getMgmtport() == null) {
			logger.error("HeartbeatManager registration of edge failed, missing data");
			return;
		}

		if (!incomingHB.containsKey(node.getNodeId())) {
			logger.info("Expects to connect to node " + node.getNodeId() + " (" + node.getHost() + ", "
					+ node.getMgmtport() + ")");

			// ensure if we reuse node instances that it is not dirty.
			node.clearAll();
			node.setInitTime(System.currentTimeMillis());
			node.setStatus(BeatStatus.Init);
			incomingHB.put(node.getNodeId(), node);
		}
	}

	/**
	 * add an INCOMING endpoint (receive HB from). This is called when this node
	 * actually establishes the connection to the node. Prior to this call, the
	 * system will register an inactive/pending node through addIncomingNode().
	 * 
	 * @param nodeId
	 * @param ch
	 * @param sa
	 */
	public void addAdjacentNodeChannel(int nodeId, Channel ch, SocketAddress sa) {
		HeartbeatData hd = incomingHB.get(nodeId);
		if (hd != null) {
			hd.setConnection(ch, sa, nodeId);
			hd.setStatus(BeatStatus.Active);

			// when the channel closes, remove it from the incomingHB list
			ch.closeFuture().addListener(new CloseHeartListener(hd));
		} else {
			logger.error("Received a HB ack from an unknown node, node ID = ", nodeId);
			// TODO actions?
		}
	}

	/**
	 * send a OUTGOING heartbeatMgr to a node. This is called when a
	 * client/server makes a request to receive heartbeats.
	 * 
	 * @param nodeId
	 * @param ch
	 * @param sa
	 */
	public void addOutgoingChannel(int nodeId, String host, int mgmtport, Channel ch, SocketAddress sa) {
		if (!outgoingHB.containsKey(ch)) {
			HeartbeatData heart = new HeartbeatData(nodeId, host, null, mgmtport);
			heart.setConnection(ch, sa, nodeId);
			outgoingHB.put(ch, heart);

			// when the channel closes, remove it from the outgoingHB
			ch.closeFuture().addListener(new CloseHeartListener(heart));
		} else {
			logger.error("Received a HB connection unknown to the server, node ID = ", nodeId);
			// TODO actions?
		}
	}

	public void release() {
		forever = true;
	}

	private Management generateHB(HeartbeatData hd) {
//		Heartbeat.Builder h = Heartbeat.newBuilder();
//		h.setTimeRef(System.currentTimeMillis());

		RaftNode thisNode = ElectionManager.getInstance().getRaftNode();
		RaftPeer peerNode = thisNode.getPeerTable().get(hd.getNodeId());


		AppendEntries.Builder ae = AppendEntries.newBuilder();
		ae.setTerm(ElectionManager.getInstance().getRaftNode().getTerm());
		ae.setCommitIndex(thisNode.getCommitIndex());

		// log entries present in local log
		if (thisNode.getLogList().size() > peerNode.getNextIndex()-1) {
//			if (peerNode.getNextIndex()-1 < 0)
//				ae.setPrevLogTerm(0);
////			logger.info("***peerNode.getNextIndex()::"+peerNode.getNextIndex());
//			else
			ae.setPrevLogTerm(thisNode.getLogEntry(peerNode.getNextIndex()-1).getTerm());
			ae.setPrevLogIndex(peerNode.getNextIndex()-1);
		} else {
			ae.setPrevLogIndex(0);
			ae.setPrevLogTerm(0);
		}

		// if new log entries present; add entries to appendEntry
		if (thisNode.getLastLogIndex() >= peerNode.getNextIndex()) {
//			ae.setCommitIndex(thisNode.getCommitIndex());

			Entry.Builder e = Entry.newBuilder();
			e.setTerm(thisNode.getLogEntry(peerNode.getNextIndex()).getTerm());
			e.setRequest(thisNode.getLogEntry(peerNode.getNextIndex()).getRequest());
//
//			e.setCommand("xxx");

			ae.setEntries(e.build());
		}

		MgmtHeader.Builder mhb = MgmtHeader.newBuilder();
		mhb.setOriginator(HeartbeatManager.conf.getNodeId());
		mhb.setTime(System.currentTimeMillis());

		// TODO token must be known between nodes
		mhb.setSecurityCode(-999);

		Management.Builder b = Management.newBuilder();
		b.setHeader(mhb.build());
//		b.setBeat(h.build());
		b.setAppendEntries(ae.build());

		logger.info("++++ sending AE to "+peerNode.getPeerId()+" with prevLogIndex "+ae.getPrevLogIndex());

		return b.build();
	}

	@Override
	public void run() {
		logger.info("starting HB manager");

		while (forever) {
			try {
				Thread.sleep(sHeartRate);

				// ignore until we have edges with other nodes
				if (outgoingHB.size() > 0 && ElectionManager.getInstance().getRaftNode().getNodeState() == RaftNode.NodeState.LEADER) {
					// TODO verify known node's status

					// send my status (heartbeatMgr)
					GeneratedMessage msg = null;
					for (HeartbeatData hd : outgoingHB.values()) {
						// if failed sends exceed threshold, stop sending
						if (hd.getFailuresOnSend() > HeartbeatData.sFailureToSendThresholdDefault) {
							// TODO mark as possible broken connection
							continue;
						}

						// only generate the message if needed
						if (msg == null)
							msg = generateHB(hd);

						try {
							if (logger.isDebugEnabled())
								logger.info("sending heartbeat to " + hd.getNodeId());
							hd.getChannel().writeAndFlush(msg);
							hd.setLastBeatSent(System.currentTimeMillis());
							hd.setFailuresOnSend(0);
							if (logger.isDebugEnabled())
								logger.info("beat (" + HeartbeatManager.conf.getNodeId() + ") sent to "
										+ hd.getNodeId() + " at " + hd.getHost());
						} catch (Exception e) {
							hd.incrementFailuresOnSend();
							logger.error("Failed " + hd.getFailures() + " times to send HB for " + hd.getNodeId()
									+ " at " + hd.getHost(), e);
						}
					}
				} else
					; // logger.info("No nodes to send HB");
			} catch (InterruptedException ie) {
				break;
			} catch (Exception e) {
				logger.error("Unexpected management communcation failure", e);
				break;
			}
		}

		if (!forever)
			logger.info("management outbound queue closing");
		else
			logger.info("unexpected closing of HB manager");

	}

	public class CloseHeartListener implements ChannelFutureListener {
		private HeartbeatData heart;

		public CloseHeartListener(HeartbeatData heart) {
			this.heart = heart;
		}

		@Override
		public void operationComplete(ChannelFuture future) throws Exception {
			if (outgoingHB.containsValue(heart)) {
				logger.warn("HB outgoing channel closing for node '" + heart.getNodeId() + "' at " + heart.getHost());
				outgoingHB.remove(future.channel());
			} else if (incomingHB.containsValue(heart)) {
				logger.warn("HB incoming channel closing for node '" + heart.getNodeId() + "' at " + heart.getHost());
				incomingHB.remove(future.channel());
			}
		}
	}
}
