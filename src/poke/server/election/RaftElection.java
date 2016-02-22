package poke.server.election;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import poke.core.Mgmt.Management;

public class RaftElection implements Election {
	protected static org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger("Raft");

//	private Integer nodeId;
//	private ElectionState current;
//	private ElectionListener listener;
	private boolean elecitonInProgress = false;

	public RaftElection() {

	}

	/**
	 * init with whoami
	 *
	 * @param nodeId
	 */
	public RaftElection(Integer nodeId) {
//		this.nodeId = nodeId;
	}


	@Override
	public void setListener(ElectionListener listener) {
		// TODO Auto-generated method stub

	}

	@Override
	public void clear() {
		// TODO Auto-generated method stub

	}

	@Override
	public boolean isElectionInprogress() {
		// TODO Auto-generated method stub
		return elecitonInProgress;
	}

	@Override
	public Integer getElectionId() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Integer createElectionID() {
		// TODO Auto-generated method stub
		return ElectionIDGenerator.nextID();
	}

	public void updateElectionID(int newElectionID) {
		ElectionIDGenerator.setMasterID(newElectionID);
	}

	@Override
	public Integer getWinner() {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public poke.core.Mgmt.Management process(poke.core.Mgmt.Management req) {
		// TODO consolidate raft process here
		return null;
	}

	@Override
	public void setNodeId(int nodeId) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void setElectionInprogress(boolean elecitonInProgress) {
		// TODO Auto-generated method stub
		this.elecitonInProgress = elecitonInProgress;
	}

}
