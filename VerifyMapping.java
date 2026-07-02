import java.sql.*;
public class VerifyMapping {
    public static void main(String[] a) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/iotp","root","2699sgly");
             Statement s = c.createStatement()) {
            System.out.println("=== Tasks + Class ===");
            ResultSet rs = s.executeQuery("SELECT t.id, t.class_id, p.project_type FROM experiment_task t JOIN experiment_project p ON t.project_id=p.id WHERE t.id IN (100,102,103,104,105,106)");
            while(rs.next()) System.out.println("task="+rs.getInt("id")+" class="+rs.getInt("class_id")+" type="+rs.getString("project_type"));
            System.out.println("\n=== Course Plans (approved) ===");
            rs = s.executeQuery("SELECT cp.id, cp.class_id, cp.course_id, c.course_name FROM course_plan cp JOIN course c ON cp.course_id=c.id WHERE cp.audit_status='APPROVED'");
            while(rs.next()) System.out.println("plan="+rs.getInt("id")+" class="+rs.getInt("class_id")+" course="+rs.getInt("course_id")+" name="+rs.getString("course_name"));
        }
    }
}
