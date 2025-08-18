import jakarta.servlet.*;
import jakarta.servlet.http.*;
import java.io.*;
import java.sql.*;

public class LoginServlet extends HttpServlet {
    protected void doPost(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {

        String username = request.getParameter("username");
        String password = request.getParameter("password");

        try {
            // Load driver
            Class.forName("com.mysql.cj.jdbc.Driver");

            // Connect to database
            Connection conn = DriverManager.getConnection(
                "jdbc:mysql://localhost:3306/db1", "root", "Login%12345");

            PreparedStatement ps = conn.prepareStatement(
                "SELECT * FROM users WHERE username = ? AND password = ?");
            ps.setString(1, username);
            ps.setString(2, password);

            ResultSet rs = ps.executeQuery();

            if (rs.next()) {
                // Login successful: set session attribute and redirect
                HttpSession session = request.getSession();
                session.setAttribute("username", username);
                response.sendRedirect("profile.jsp");
            } else {
                // Login failed: redirect back to login page or show error
                response.sendRedirect("index.html?error=invalid_credentials");
            }

            rs.close();
            ps.close();
            conn.close();

        } catch (Exception e) {
            e.printStackTrace();
            // Optionally, redirect to an error page or show message
            response.sendRedirect("index.html?error=server_error");
        }
    }
}
