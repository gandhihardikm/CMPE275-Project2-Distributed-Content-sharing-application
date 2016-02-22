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

import java.beans.Beans;
import java.util.concurrent.atomic.AtomicReference;
import java.util.HashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.core.Mgmt.LeaderElection;
import poke.core.Mgmt.LeaderElection.ElectAction;
import poke.core.Mgmt.Management;
import poke.core.Mgmt.MgmtHeader;
import poke.core.Mgmt.RequestVote;
import poke.core.Mgmt.RequestVoteResponse;
import poke.core.Mgmt.VectorClock;
import poke.server.conf.ServerConf;
import poke.server.election.Election;
import poke.server.election.ElectionListener;
import poke.server.election.FloodMaxElection;
import poke.server.election.RaftElectionManager;
import poke.server.election.RandomTimeout;
import poke.server.election.raft.RaftNode;
import poke.server.election.raft.RaftPeer;
import poke.server.voting.Vote;

/**
 * The election manager is used to determine leadership within the network. The
 * leader is not always a central point in which decisions are passed. For
 * instance, a leader can be used to break ties or act as a scheduling dispatch.
 * However, the dependency on a leader in a decentralized design (or any design,
 * matter of fact) can lead to bottlenecks during heavy, peak loads.
 * 
 * TODO An election is a special case of voting. We should refactor to use only
 * voting.
 * 
 * QUESTIONS:
 * 
 * Can we look to the PAXOS alg. (see PAXOS Made Simple, Lamport, 2001) to model
 * our behavior where we have no single point of failure; each node within the
 * network can act as a consensus requester and coordinator for localized
 * decisions? One can envision this as all nodes accept jobs, and all nodes
 * request from the network whether to accept or reject the request/job.
 * 
 * Does a 'random walk' approach to consistent data replication work?
 * 
 * What use cases do we would want a central coordinator vs. a consensus
 * building model? How does this affect liveliness?
 * 
 * Notes:
 * <ul>
 * <li>Communication: the communication (channel) established by the heartbeat
 * manager is used by the election manager to communicate elections. This
 * creates a constraint that in order for internal (mgmt) tasks to be sent to
 * other nodes, the heartbeat must have already created the channel.
 * </ul>
 * 
 * @author gash
 * 
 */
public class ElectionManager implements ElectionListener {
	protected static Logger logger = LoggerFactory.getLogger("election");
	protected static AtomicReference<ElectionManager> instance = new AtomicReference<ElectionManager>();
	protected static HashMap<Integer, String> pingList=new HashMap<Integer, String>();

	private static ServerConf conf;

	// number of times we try to get the leader when a node starts up
	private int firstTime = 2;

	protected static RaftElectionManager raftManagerThread = RaftElectionManager.resetTimer(null);

	/** The election that is in progress - only ONE! */
	private Election election;
	private int electionCycle = -1;
	private Integer syncPt = 1;

	/** The leader */
	private Integer leaderNode = -1;

	private RaftNode raftNode;

	/** Majority counter **/
	private CountDownLatch votesRequired;

	public static ElectionManager initManager(ServerConf conf) {
		ElectionManager.conf = conf;
		instance.compareAndSet(null, new ElectionManager());

		// Starting Thread
		raftManagerThread.start();

		instance.get().raftNode = RaftNode.getInstance(conf.getNodeId(), RaftNode.NodeState.FOLLOWER);
		return instance.get();
	}


	public RaftNode getRaftNode() {
		return raftNode;
	}

	public Integer getLeaderNode() {
		return leaderNode;
	}

	public void setLeaderNode(int leaderNode) {
		this.leaderNode = leaderNode;
	}

	/**
	 * Access a consistent instance for the life of the process.
	 * 
	 * TODO do we need to have a singleton for this? What happens when a process
	 * acts on behalf of separate interests?
	 * 
	 * @return
	 */
	public static ElectionManager getInstance() {
		// TODO throw exception if not initialized!
		return instance.get();
	}

	public static ServerConf getConf() {
		return conf;
	}

	public CountDownLatch getVotesRequired() {
		return votesRequired;
	}

	public void setVotesRequired(CountDownLatch votesRequired) {
		this.votesRequired = votesRequired;
	}

	public Election getElection() {
		return election;
	}

	/**
	 * returns the leader of the network
	 * 
	 * @return
	 */
	public Integer whoIsTheLeader() {
		return this.leaderNode;
	}

	/**
	 * initiate an election from within the server - most likely scenario is the
	 * heart beat manager detects a failure of the leader and calls this method.
	 * 
	 * Depending upon the algo. used (bully, flood, lcr, hs) the
	 * manager.startElection() will create the algo class instance and forward
	 * processing to it. If there was an election in progress and the election
	 * ID is not the same, the new election will supplant the current (older)
	 * election. This should only be triggered from nodes that share an edge
	 * with the leader.
	 */
	public void startElection() {
		if (raftNode.getNodeState() != RaftNode.NodeState.CANDIDATE) {
			logger.warn(conf.getNodeId()+" is not a candidate!");
			return;
		}

		election = electionInstance();
		election.setElectionInprogress(true);
		electionCycle = election.createElectionID();

		raftNode.setTerm(electionCycle);

		raftNode.setVotedFor(raftNode.getNodeId());

		RequestVote.Builder rv = RequestVote.newBuilder();
		rv.setTerm(electionCycle);

		rv.setCandidateId(conf.getNodeId());
		rv.setLastLogIndex(raftNode.getLastLogIndex());
		rv.setLastLogterm(raftNode.getLastLogTerm());

		MgmtHeader.Builder mhb = MgmtHeader.newBuilder();
		mhb.setOriginator(conf.getNodeId());
		mhb.setTime(System.currentTimeMillis());
		mhb.setSecurityCode(-999); // TODO add security

		/*VectorClock.Builder rpb = VectorClock.newBuilder();
		rpb.setNodeId(conf.getNodeId());
		rpb.setTime(mhb.getTime());
		rpb.setVersion(electionCycle);
		mhb.addPath(rpb);*/

		Management.Builder mb = Management.newBuilder();
		mb.setHeader(mhb.build());
		//mb.setElection(elb.build());
		mb.setRequestVote(rv.build());

		// now send it out to all my edges
		logger.info("***Election started by node " + raftNode.getNodeId()+" for term "+raftNode.getTerm());
		ConnectionManager.broadcast(mb.build());
		logger.info("***RequestVote boradacsted by " + raftNode.getNodeId()+" for term "+raftNode.getTerm());

		// TODO wait until this node gets majority or another leader is there or election timeout
		try {
			// election timeout
			votesRequired.await(RandomTimeout.generateElectionTimeout(), TimeUnit.MILLISECONDS);
			if (votesRequired.getCount() != 0) {
				logger.info("***Election timeout..");
			} else {
				if (raftNode.getNodeState() == RaftNode.NodeState.CANDIDATE) {
					raftNode.setNodeState(RaftNode.NodeState.LEADER);
					//logger.info("***Majority Achieved by node " + raftNode.getNodeId()+" for term "+raftNode.getTerm());
					election.setElectionInprogress(false);
					election = null;
					// update nextIndex of peers
					raftNode.initializePeerNodes();
					//ConnectionManager.addPingList(90, "192.168.0.105");
					//ConnectionManager.addPingList(91, "192.168.0.102");
					//ConnectionManager.addPingList(92, "192.168.0.104");
					//pingList.put(90,"192.168.0.103");
					//pingList.put(91,"192.168.0.104");
					//logger.info("At Start of Pinging");
					//logger.info("Before for");
					//ConnectionManager.ping(PingRequest.getPingRequest());
					/*for(String s :pingList.values()){
						CommConnection ch= new CommConnection(s, 5570, PingRequest.getPingRequest());
						ch.sendToLeader();
						logger.info("Ping Sent to ip -->"+s);
					}*/
				}
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} // waits for majority..needs better approach to cover all 3 possibilities
	}

	/**
	 * collect votes
	 */
	public void collectVotes(Management mgmt) {
		if (!mgmt.hasRequestVoteResponse() || raftNode.getNodeState() != RaftNode.NodeState.CANDIDATE) {
			return;
		}

		RequestVoteResponse rvr = mgmt.getRequestVoteResponse();

		if (rvr.getVoteGranted()) {
			logger.info("RequestVoteResponse received from "+rvr.getVoterId()+" for term "+rvr.getTerm());

			// check if election still in progress
			/*if ( ) {

			}*/

			// check vote is for current term/electionCycle
			//		if (rvr.getTerm() != electionCycle) {
			//			return;
			//		}
			RaftNode thisNode = ElectionManager.getInstance().getRaftNode();
			RaftPeer peerNode = thisNode.getPeerTable().get(mgmt.getHeader().getOriginator());
			peerNode.setVoteGranted(rvr.getVoteGranted());

			votesRequired.countDown();
		}
	}

	/**
	 * @param args
	 */
	public void processRequest(Management mgmt) {
		if (!mgmt.hasRequestVote())
			return;

		RequestVote rv = mgmt.getRequestVote();

		// TODO - consolidate raft safety mechanism at one place
		logger.info("***RequestVote received from "+rv.getCandidateId()+" for term "+rv.getTerm());

		respondToRequestVote(mgmt);

		// Comments since not required for RAFT

		/*LeaderElection req = mgmt.getElection();

		// when a new node joins the network it will want to know who the leader
		// is - we kind of ram this request-response in the process request
		// though there has to be a better place for it
		if (req.getAction().getNumber() == LeaderElection.ElectAction.WHOISTHELEADER_VALUE) {
			respondToWhoIsTheLeader(mgmt);
			return;
		} else if (req.getAction().getNumber() == LeaderElection.ElectAction.THELEADERIS_VALUE) {
			logger.info("Node " + conf.getNodeId() + " got an answer on who the leader is. Its Node "
					+ req.getCandidateId());
			this.leaderNode = req.getCandidateId();
			return;
		}

		// else fall through to an election

		if (req.hasExpires()) {
			long ct = System.currentTimeMillis();
			if (ct > req.getExpires()) {
				// ran out of time so the election is over
				election.clear();
				return;
			}
		}

		Management rtn = electionInstance().process(mgmt);
		if (rtn != null)
			ConnectionManager.broadcast(rtn);*/
	}

	public boolean ensureSafety(RequestVote rv) {
		if (rv.getTerm() == raftNode.getTerm()) {
			// already voted in this term so ignore
			logger.info("***Already voted for term "+raftNode.getTerm());
			return false;
		}

		if (rv.getTerm() < raftNode.getTerm()) {
			// requester node is not up-to-date, so can't get vote
			logger.info("***Requester "+rv.getCandidateId()+" is not up to date!");
			return false;
		}

		if (rv.getLastLogterm() < raftNode.getLastLogTerm()) {
			// requester last log term is behind this node's last log term; so can't vote
			logger.info("Requester "+rv.getCandidateId()+" log term doesnot match!");
			return false;
		}

		if (rv.getLastLogterm() == raftNode.getLastLogTerm() && rv.getLastLogIndex() < raftNode.getLastLogIndex()) {
			// requester last log term is behind this node's last log index; so can't vote
			logger.info("Requester "+rv.getCandidateId()+" log index doesnot match!");
			return false;
		}

		return true;

	}

	public void respondToRequestVote(Management mgmt) {
		RequestVote rv = mgmt.getRequestVote();
		RequestVoteResponse.Builder rvr = RequestVoteResponse.newBuilder();
		boolean isSafe = ensureSafety(rv);
		if (isSafe) {
			raftNode.setTerm(rv.getTerm());
			raftNode.setVotedFor(rv.getCandidateId());

			logger.info("Node "+conf.getNodeId()+" is voting for Node "+rv.getCandidateId());

			rvr.setTerm(rv.getTerm())
					.setVoterId(conf.getNodeId())
					.setVoteGranted(true);
		} else {
			logger.info("Node "+conf.getNodeId()+" is not voting for Node "+rv.getCandidateId());

			rvr.setTerm(raftNode.getTerm())
					.setVoterId(conf.getNodeId())
					.setVoteGranted(false);
		}

		MgmtHeader.Builder mhb = MgmtHeader.newBuilder();
		mhb.setOriginator(conf.getNodeId());
		mhb.setTime(System.currentTimeMillis());
		mhb.setSecurityCode(-999);

		Management.Builder mb = Management.newBuilder();
		mb.setHeader(mhb.build());
		mb.setRequestVoteResponse(rvr.build());

		// now send it to the requester
		try {
			ConnectionManager.getConnection(mgmt.getHeader().getOriginator(), true).writeAndFlush(mb.build());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	/**
	 * check the health of the leader (usually called after a HB update)
	 * 
	 * @param mgmt
	 */
	public void assessCurrentState(Management mgmt) {
		// logger.info("ElectionManager.assessCurrentState() checking elected leader status");

		/*if (firstTime > 0 && ConnectionManager.getNumMgmtConnections() > 0) {
			// give it two tries to get the leader
			this.firstTime--;
			askWhoIsTheLeader();
		} else if (leaderNode == null && (election == null || !election.isElectionInprogress())) {
			// if this is not an election state, we need to assess the H&S of
			// the network's leader
			synchronized (syncPt) {
				startElection();
			}
		}*/
	}

	/** election listener implementation */
	@Override
	public void concludeWith(boolean success, Integer leaderID) {
		if (success) {
			logger.info("-------> the leader is " + leaderID);
			this.leaderNode = leaderID;
		}

		election.clear();
	}

	private void respondToWhoIsTheLeader(Management mgmt) {
		if (this.leaderNode == null) {
			logger.info("------> I cannot respond to who the leader is! I don't know!");
			return;
		}

		logger.info("Node " + conf.getNodeId() + " is replying to " + mgmt.getHeader().getOriginator()
				+ "'s request who the leader is. Its Node " + this.leaderNode);

		MgmtHeader.Builder mhb = MgmtHeader.newBuilder();
		mhb.setOriginator(conf.getNodeId());
		mhb.setTime(System.currentTimeMillis());

		VectorClock.Builder rpb = VectorClock.newBuilder();
		rpb.setNodeId(conf.getNodeId());
		rpb.setTime(mhb.getTime());
		rpb.setVersion(electionCycle);
		mhb.addPath(rpb);

		LeaderElection.Builder elb = LeaderElection.newBuilder();
		elb.setElectId(electionCycle);
		elb.setAction(ElectAction.THELEADERIS);
		elb.setDesc("Node " + this.leaderNode + " is the leader");
		elb.setCandidateId(this.leaderNode);
		elb.setExpires(-1);

		Management.Builder mb = Management.newBuilder();
		mb.setHeader(mhb.build());
		mb.setElection(elb.build());

		// now send it to the requester
		logger.info("Election started by node " + conf.getNodeId());
		try {

			ConnectionManager.getConnection(mgmt.getHeader().getOriginator(), true).write(mb.build());
		} catch (Exception ex) {
			ex.printStackTrace();
		}
	}

	private void askWhoIsTheLeader() {
		logger.info("Node " + conf.getNodeId() + " is searching for the leader");

		MgmtHeader.Builder mhb = MgmtHeader.newBuilder();
		mhb.setOriginator(conf.getNodeId());
		mhb.setTime(System.currentTimeMillis());
		mhb.setSecurityCode(-999); // TODO add security

		VectorClock.Builder rpb = VectorClock.newBuilder();
		rpb.setNodeId(conf.getNodeId());
		rpb.setTime(mhb.getTime());
		rpb.setVersion(electionCycle);
		mhb.addPath(rpb);

		LeaderElection.Builder elb = LeaderElection.newBuilder();
		elb.setElectId(-1);
		elb.setAction(ElectAction.WHOISTHELEADER);
		elb.setDesc("Node " + this.leaderNode + " is asking who the leader is");
		elb.setCandidateId(-1);
		elb.setExpires(-1);

		Management.Builder mb = Management.newBuilder();
		mb.setHeader(mhb.build());
		mb.setElection(elb.build());

		// now send it to the requester
		ConnectionManager.broadcast(mb.build());
	}

	private Election electionInstance() {
		if (election == null) {
			synchronized (syncPt) {
				if (election !=null)
					return election;
				
				// new election
				String clazz = ElectionManager.conf.getElectionImplementation();

				// if an election instance already existed, this would
				// override the current election
				try {
					election = (Election) Beans.instantiate(this.getClass().getClassLoader(), clazz);
					election.setNodeId(conf.getNodeId());
					election.setListener(this);

					// this sucks - bad coding here! should use configuration
					// properties
					if (election instanceof FloodMaxElection) {
						logger.warn("Node " + conf.getNodeId() + " setting max hops to arbitrary value (4)");
						((FloodMaxElection) election).setMaxHops(4);
					}

				} catch (Exception e) {
					logger.error("Failed to create " + clazz, e);
				}
			}
		}

		return election;

	}
}
