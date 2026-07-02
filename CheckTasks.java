import java.sql.*;
public class CheckTasks {
    public static void main(String[] a) throws Exception {
        Class.forName("com.mysql.cj.jdbc.Driver");
        try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/iotp","root","2699sgly");
             Statement s = c.createStatement()) {
            ResultSet rs = s.executeQuery("SELECT t.id as task_id, t.project_id, p.project_name, p.project_type FROM experiment_task t JOIN experiment_project p ON t.project_id=p.id WHERE t.id IN (100,102,103,104,105,106)");
            while(rs.next()) System.out.println("task="+rs.getInt("task_id")+" project="+rs.getInt("project_id")+" name="+rs.getString("project_name")+" type="+rs.getString("project_type"));
        }
    }
}
