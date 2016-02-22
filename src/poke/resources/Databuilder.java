package poke.resources;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;

public class Databuilder {

	protected static Logger logger = LoggerFactory.getLogger("Data Builder");
	public static void main(String[] args) {
		generateData();
	}

	public static void generateData() {

		logger.info("Generating Data to serve Client request");

		// DataResource used for initilize hashmap that will store course and user details
		DataResource res = new DataResource();

		// Store course data in Hashmap
		// Store User data in Hashmap
		for (int i = 1; i < 9; i++) {
			res.addCourse("CMPE27" + i, "Course Description-" + i);
			res.addUser("Student-" + i);
		}

		// Store Course related to user into Hashmap
		for (int i = 1; i < 9; i++) {
			for (int j = 1; j < 5; j++) {
				res.addUserCourse(i, j);
			}
		}


		logger.info("Data generated.");
/*		DataResource res1 = new DataResource();
		
		DataResource.Users u = res1.getUserDetails(2);*/

		/*String result = "";
		String lstUsercourses = "";
		
		if (u != null) {
			lstUsercourses = lstUsercourses + "[";
			for(DataResource.UserCourses uc : u.listUserCourse){
				lstUsercourses = lstUsercourses + "{";
				lstUsercourses = lstUsercourses + "UserId:" + uc.UserId + ", CourseId:" + uc.CourseId;
				lstUsercourses = lstUsercourses + "},";
			}
			
			lstUsercourses = lstUsercourses + "]";
			result = "{UserId: " + u.UserId + ", UserName: " + u.UserName + ", UserCourses: " + lstUsercourses + "}";

			System.out.println("User Details Results------------------:"+result);
		}*/

		/*HashMap<Integer, DataResource.Course> a = res1.getAllCourseDetails();

		HashMap<Integer, DataResource.Users> b = res1.getAllUserDetails();*/
	}
}
