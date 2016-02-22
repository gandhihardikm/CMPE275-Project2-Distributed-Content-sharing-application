package poke.server.election;

import java.util.Random;
import java.util.TimerTask;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.comm.App.NameValueSet.NodeType;
import poke.core.Mgmt;
import poke.server.election.raft.RaftNode;
import poke.server.managers.ElectionManager;
import poke.server.managers.HeartbeatData;
import poke.server.managers.HeartbeatManager;

import poke.server.managers.ElectionManager;
import poke.server.conf.ServerConf;
import poke.server.conf.NodeDesc;
import poke.client.comm.CommConnection;

import java.util.TreeMap;




public class RaftElectionManager extends Thread {
	protected static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger("***Raft Election");
	private static java.util.concurrent.atomic.AtomicReference<poke.server.election.RaftElectionManager> raftInstance = new java.util.concurrent.atomic.AtomicReference<poke.server.election.RaftElectionManager>();
//	private static final int MINTIMEOUT = 5000; // in milis

	private RaftElectionManager() {}

	public static RaftElectionManager getInstance() {
		//raftInstance.compareAndSet(null, new RaftElection());
		return raftInstance.get();
	}

	public static RaftElectionManager resetTimer(RaftElectionManager oldTimer) {
		raftInstance.compareAndSet(oldTimer, new RaftElectionManager());
		System.out.println(raftInstance.get());
		return raftInstance.get();
	}

	@Override
	public void run() {
		poke.server.managers.ElectionManager em = poke.server.managers.ElectionManager.getInstance();
		while(true) {
			try {
				// Random timeout for
				Thread.sleep(RandomTimeout.generateTimeout());
				// get leadernode
				Integer leaderNode = em.whoIsTheLeader();
				logger.info(em.getRaftNode().toString());
				//
				if (em.getRaftNode().getNodeState() != poke.server.election.raft.RaftNode.NodeState.LEADER) {
					poke.server.managers.HeartbeatData hd = poke.server.managers.HeartbeatManager.getInstance().getIncomingHB().get(leaderNode);

					if (hd != null && hd.getIsLastBeat() == true) {
						logger.info("***HB received from leader.. "+leaderNode.intValue());
						hd.setIsLastBeat(false);
					} else {
						logger.info("***HB not received from leader "+leaderNode.intValue());
						//majority requirement; candidate votes for himself
						em.setVotesRequired(new java.util.concurrent.CountDownLatch(poke.server.managers.ElectionManager.getConf().getRaftMajority()-1));
						// TODO - remove below condition-- only for testing
//						if (em.getConf().getNodeId() == 2)
//							continue;

						em.getRaftNode().setNodeState(poke.server.election.raft.RaftNode.NodeState.CANDIDATE);
						em.setLeaderNode(-1);
						em.startElection();

						// TODO wait until this node gets majority or another leader is there or election timeout

						/*try {
							// election timeout
							ElectionManager.getInstance().getVotesRequired().await();//, TimeUnit.SECONDS);
							if (ElectionManager.getInstance().getVotesRequired().getCount() != 0) {
								logger.info("***Election timeout..");
								continue;
							}

							logger.info("***Majority Achieved by node " + ElectionManager.getInstance().getConf().getNodeId());

							ElectionManager.getInstance().setNodeState(NodeState.LEADER);

						} catch (InterruptedException e) {
							e.printStackTrace();
						}*/ // waits for majority..needs better approach to cover all 3 possibilities

					}
					// TODO once leader is set using RAFT remove this else part and use enum in if comparision
				} else {
					// if leader send append entries...
					logger.info("*** This is the LEADER!!! *** for term " + poke.server.managers.ElectionManager.getInstance().getRaftNode().getTerm());



//					em.getRaftNode().setNodeState(RaftNode.NodeState.LEADER);
			//			ElectionManager.getInstance().setNodeState(NodeState.CANDIDATE);
			//			ElectionManager.getInstance().startElection();
				}
			} catch (InterruptedException e) {
				e.printStackTrace();
			}	
		} 
	}

}
