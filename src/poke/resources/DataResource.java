package poke.resources;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

public class DataResource {

	public static HashMap<Integer, Course> listOfCourses = new HashMap<Integer, Course>();
	public static HashMap<Integer, Users> userDetails = new HashMap<Integer, Users>();
	public static Integer udStartIndex = 1;
	public static Integer lcStartIndex = 1;


	// Return User details for given user id
	public Users getUserDetails(int userId) {
		return userDetails.get(userId);
	}

	// Return all courses details
	public HashMap<Integer, Course> getAllCourseDetails(){
		return this.listOfCourses;
	}

	// Return all users details
	public HashMap<Integer, Users> getAllUserDetails(){
		return this.userDetails;
	}

	// Return course details for given course id
	public Course getCourseDetails(int courseId) {
		return listOfCourses.get(courseId);
	}

	// Store course name and course description to listofCourses hashmap
	public boolean addCourse(String courseName, String courseDescription) {
		int key = lcStartIndex;
		listOfCourses.put(key, new Course(key, courseName, courseDescription));
		lcStartIndex++;
		return true;
	}

	// Add username to userDetails hashmap
	public boolean addUser(String username) {
		List<UserCourses> userDetailsList = new ArrayList<UserCourses>();
		int key = udStartIndex;
		userDetails.put(key, new Users(key, username, userDetailsList));
		udStartIndex++;
		return true;
	}

	// Store Courses related to specific user
	public boolean addUserCourse(int userId, int courseId) {
		Users details = userDetails.get(userId);
		details.listUserCourse.add(new UserCourses(userId, courseId));
		return true;
	}

	// Return Course list for specific user
	public List<UserCourses> getUserCourses(int userId) {
		Users details = userDetails.get(userId);
		return details.listUserCourse;
	}

	// Course class developed to handle details regarding courses
	public static class Course {
		Integer CourseId;
		String CourseName;
		String CourseDescription;

		public Course(int courseId, String courseName, String courseDesc) {
			this.CourseId = courseId;
			this.CourseName = courseName;
			this.CourseDescription = courseDesc;
		}
	}

	// Users class designed to handle details regading users
	public static class Users {
		Integer UserId;
		String UserName;
		List<UserCourses> listUserCourse = new ArrayList<UserCourses>();

		public Users(int userId, String userName, List<UserCourses> userCourses) {
			this.UserId = userId;
			this.UserName = userName;
			this.listUserCourse = userCourses;
		}
	}

	// Courses taken by individual user
	public static class UserCourses {
		Integer UserId;
		Integer CourseId;

		public UserCourses(int userId, int courseId) {
			this.UserId = userId;
			this.CourseId = courseId;
		}
	}
}
