package poke.resources;

import poke.comm.App;
import poke.comm.App.Request;
import poke.comm.App.PokeStatus;
import poke.server.managers.ConnectionManager;
import poke.server.resources.Resource;
import io.netty.channel.Channel;
import poke.server.resources.ResourceUtil;
import java.util.HashMap;
import java.util.TreeMap;
import poke.server.managers.ElectionManager;
import poke.server.conf.ServerConf;
import poke.server.conf.NodeDesc;
import poke.client.comm.CommConnection;


public class JobResource implements Resource {

	@Override
	public void process(Request request, Channel ch) {

		System.out.println("Leaddddddddderrrrrr node : "+ElectionManager.getInstance().getLeaderNode());
		System.out.println("SSSSSEEEEEFFFTTTTTT node : "+ElectionManager.getInstance().getRaftNode().getNodeId());


		App.ClientMessage msg = request.getBody().getClientMessage();
		System.out.println("User Id--------*********:" + msg.getFunctionality());


		System.out.println("Requset contents:"+request.getBody().getResult());
		if(request.getBody().getResult().equalsIgnoreCase("100")){


			System.out.println("--------------Getting result from leader---------------------------------");
			Request.Builder rb = Request.newBuilder();
			//App.ClientMessage msg1 = request.getBody().getClientMessage();
			App.Payload.Builder pb = App.Payload.newBuilder();
			App.Ping.Builder fb = App.Ping.newBuilder();
			fb.setTag(request.getBody().getPing().getTag());
			fb.setNumber(request.getBody().getPing().getNumber());
			pb.setResult(request.getBody().getResult());
			pb.setPing(fb.build());
			rb.setBody(pb.build());

			Request reply = rb.build();
			System.out.println(reply);
			ch.writeAndFlush(reply);
		}
		//return null;
		if(msg.getFunctionality()  == App.ClientMessage.Functionalities.GETSUSER) {
			ConnectionManager.addclientconnection(msg.getClientId(), ch);
			DataResource res1 = new DataResource();
			DataResource.Users u = res1.getUserDetails(msg.getDetails().getUserId());
			System.out.println("User Details------------------:" + u);


			String result = "";
			String lstUsercourses = "";

			if (u != null) {
				lstUsercourses = lstUsercourses + "[";
				for (DataResource.UserCourses uc : u.listUserCourse) {
					lstUsercourses = lstUsercourses + "{";
					lstUsercourses = lstUsercourses + "UserId:" + uc.UserId + ", CourseId:" + uc.CourseId;
					lstUsercourses = lstUsercourses + "},";
				}

				lstUsercourses = lstUsercourses + "]";
				result = "{UserId: " + u.UserId + ", UserName: " + u.UserName + ", UserCourses: " + lstUsercourses + "}";

				System.out.println("User Details Results------------------:" + result);
				Request.Builder rb = Request.newBuilder();
				rb.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(), PokeStatus.SUCCESS, null));
				// payload
				App.Payload.Builder pb = App.Payload.newBuilder();
				App.Ping.Builder fb = App.Ping.newBuilder();
				fb.setTag(request.getBody().getPing().getTag());
				fb.setNumber(request.getBody().getPing().getNumber());
				System.out.println("Result is ::::::" + result);
				pb.setResult(result);
				pb.setPing(fb.build());
				rb.setBody(pb.build());

				Request reply = rb.build();
				System.out.println(reply);
				ch.writeAndFlush(reply);
			}
			else
			{
				if(ElectionManager.getInstance().getLeaderNode() == ElectionManager.getInstance().getRaftNode().getNodeId())
				{
					result = "100";
					Request.Builder rb = Request.newBuilder();
					rb.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(), PokeStatus.SUCCESS, null));
					// payload
					App.Payload.Builder pb = App.Payload.newBuilder();
					App.Ping.Builder fb = App.Ping.newBuilder();
					fb.setTag(request.getBody().getPing().getTag());
					fb.setNumber(request.getBody().getPing().getNumber());
					System.out.println("Result is ::::::" + result);
					pb.setResult(result);
					pb.setPing(fb.build());
					rb.setBody(pb.build());

					Request reply = rb.build();
					System.out.println(reply);
					System.out.println("host" + ch.localAddress());
					//ch.writeAndFlush(reply);
					/*try{

						CommConnection commConnection = new CommConnection("localhost", 5570);
						commConnection.sendMessage(request);
						CommConnection commConnection1 = new CommConnection("localhost", 5571);
						commConnection1.sendMessage(request);
					}
					catch(Exception e){
						System.out.println("fffffffERRROR");
					}*/

				}else
				{
					try {

						Integer leaderNode = ElectionManager.getInstance().getLeaderNode();
						ServerConf serverConf = ElectionManager.getInstance().getConf();

						serverConf.getPort();
						TreeMap<Integer, NodeDesc> treeMap = serverConf.getAdjacent().getAdjacentNodes();

						NodeDesc leaderNodeDesc = treeMap.get(leaderNode);
						String leaderHost = leaderNodeDesc.getHost();
						int leaderPort = leaderNodeDesc.getPort();
						System.out.println("fahsgfjhasjfgasfas");
						CommConnection commConnection = new CommConnection(leaderHost, leaderPort);
						commConnection.sendMessage(request);

					}catch (Exception e){
						System.out.println("error");
					}

				}

			}
		}
		else if(msg.getFunctionality()  == App.ClientMessage.Functionalities.GETCOURSEDESCRIPTION){
			DataResource res1 = new DataResource();
			HashMap<Integer, DataResource.Course> a = res1.getAllCourseDetails();
			System.out.println("Course Details------------------:" + a);

			Request.Builder rb = Request.newBuilder();

			String result = "";
			String lstcourses = "{Course Details:";

			if (a != null) {

				for(Integer key : a.keySet()) {
					lstcourses = lstcourses + "{\nCourse Id:" + key + ",\nCourse Name:" + a.get(key).CourseName + ",\nCourse Description:" + a.get(key).CourseDescription + "\n},\n";

				}
				lstcourses = lstcourses + "}";
				System.out.println("Course Details Results------------------:" + lstcourses);
			}

			rb.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(), PokeStatus.SUCCESS, null));


			// payload
			App.Payload.Builder pb = App.Payload.newBuilder();
			App.Ping.Builder fb = App.Ping.newBuilder();
			fb.setTag(request.getBody().getPing().getTag());
			fb.setNumber(request.getBody().getPing().getNumber());
			pb.setResult(lstcourses);
			pb.setPing(fb.build());
			rb.setBody(pb.build());


			Request reply = rb.build();
			System.out.println(reply);
			ch.writeAndFlush(reply);
		}
		else {
			DataResource res1 = new DataResource();
			HashMap<Integer, DataResource.Users> user = res1.getAllUserDetails();
			System.out.println("User Details------------------:" + user);

			Request.Builder rb = Request.newBuilder();

			//String result = "";
			String lstcourses = "{User Details:";

			if (user != null) {

				for(Integer key : user.keySet()) {
					lstcourses = lstcourses + "{\nUser Id:" + key + ",\nUser Name:" + user.get(key).UserName + ",";
					for (DataResource.UserCourses uc : user.get(key).listUserCourse) {
						lstcourses = lstcourses + "\n{";
						lstcourses = lstcourses + "UserId:" + uc.UserId + ", CourseId:" + uc.CourseId;
						lstcourses = lstcourses + "},";
					}
				}
				lstcourses = lstcourses + "}";
				System.out.println("User Details Results------------------:" + lstcourses);
			}

			rb.setHeader(ResourceUtil.buildHeaderFrom(request.getHeader(), PokeStatus.SUCCESS, null));


			// payload
			App.Payload.Builder pb = App.Payload.newBuilder();
			App.Ping.Builder fb = App.Ping.newBuilder();
			fb.setTag(request.getBody().getPing().getTag());
			fb.setNumber(request.getBody().getPing().getNumber());
			pb.setResult(lstcourses);
			pb.setPing(fb.build());
			rb.setBody(pb.build());


			Request reply = rb.build();
			System.out.println(reply);
			ch.writeAndFlush(reply);
		}
	}

}