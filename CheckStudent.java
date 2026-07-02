import java.sql.*;
public class CheckStudent {
    public static void main(String[] a) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/iotp","root","2699sgly");
             Statement s = c.createStatement()) {
            // 找吴晓丽
            ResultSet rs = s.executeQuery("SELECT id, username, real_name FROM sys_user WHERE real_name LIKE '%吴晓丽%' OR username LIKE '%吴晓丽%'");
            while(rs.next()) System.out.println("User: id="+rs.getInt("id")+" username="+rs.getString("username")+" name="+rs.getString("real_name"));
            // 查她的成绩
            int uid = 0;
            rs = s.executeQuery("SELECT id FROM sys_user WHERE real_name LIKE '%吴晓丽%' LIMIT 1");
            if(rs.next()) uid = rs.getInt("id");
            if(uid > 0) {
                System.out.println("\n--- Grades ---");
                rs = s.executeQuery("SELECT g.id, g.final_grade, g.experiment_grade, g.training_grade, g.is_published, cp.course_id FROM student_grade g LEFT JOIN course_plan cp ON g.course_plan_id=cp.id WHERE g.student_id="+uid);
                while(rs.next()) System.out.println("grade_id="+rs.getInt("id")+" final="+rs.getBigDecimal("final_grade")+" exp="+rs.getBigDecimal("experiment_grade")+" train="+rs.getBigDecimal("training_grade")+" published="+rs.getInt("is_published")+" course_id="+rs.getObject("course_id"));
                System.out.println("\n--- Submissions ---");
                rs = s.executeQuery("SELECT s.id, s.task_id, s.score, s.status FROM student_experiment_submission s WHERE s.student_id="+uid);
                while(rs.next()) System.out.println("sub_id="+rs.getInt("id")+" task="+rs.getInt("task_id")+" score="+rs.getBigDecimal("score")+" status="+rs.getString("status"));
                System.out.println("\n--- Daily Analytics ---");
                rs = s.executeQuery("SELECT stat_date, course_id, study_duration_minutes FROM learning_analytics_daily WHERE student_id="+uid+" ORDER BY stat_date DESC LIMIT 10");
                while(rs.next()) System.out.println("date="+rs.getDate("stat_date")+" course="+rs.getInt("course_id")+" minutes="+rs.getInt("study_duration_minutes"));
                System.out.println("\n--- Academic Diagnosis ---");
                rs = s.executeQuery("SELECT * FROM academic_diagnosis WHERE student_id="+uid+" ORDER BY generated_time DESC LIMIT 1");
                while(rs.next()) System.out.println("level="+rs.getString("diagnosis_level")+" score="+rs.getBigDecimal("overall_score")+" time="+rs.getTimestamp("generated_time"));
            }
        }
    }
}
